# TechStore API

API RESTful para la gestión del catálogo de productos de la tienda ficticia **TechStore Chile**, desarrollada con Spring Boot como parte de la asignatura Java: Diseño y Construcción de Soluciones Nativas en Nube (Duoc UC).

## Descripción

El servicio expone operaciones CRUD sobre productos (creación, consulta, edición y eliminación lógica), protegidas mediante autenticación **JWT**, y persiste la información en una base de datos **PostgreSQL**. Cada operación de escritura (crear, modificar, eliminar) publica de forma asíncrona un evento de auditoría a una cola de **Amazon SQS**, que es procesado por una función **AWS Lambda** para registrar la transacción en Amazon CloudWatch Logs.

## Tecnologías

- Java 17 / Spring Boot 4.0.6
- Spring Data JPA + PostgreSQL
- Spring Security + JJWT (autenticación basada en tokens)
- AWS SDK v2 (Amazon SQS)
- Docker (build multi-stage)
- Amazon ECS Fargate, ECR, ALB, RDS, SQS, Lambda, API Gateway (infraestructura en AWS)
- GitHub Actions (CI/CD)

## Endpoints principales

| Método | Endpoint | Descripción | Requiere JWT |
|---|---|---|---|
| POST | `/auth/login` | Autenticación, devuelve un token JWT | No |
| GET | `/api/productos` | Lista todos los productos | Sí |
| POST | `/api/productos` | Crea un nuevo producto | Sí |
| PUT | `/api/productos/{id}` | Modifica un producto existente | Sí |
| DELETE | `/api/productos/{id}` | Elimina lógicamente un producto | Sí |
| GET | `/` | Health check (usado por el ALB) | No |

Las peticiones a endpoints protegidos deben incluir el header `Authorization: Bearer <token>`.

## Cómo ejecutar el proyecto localmente

### Requisitos previos
- Java 17
- Maven
- Docker (opcional, para levantar PostgreSQL o correr la app contenerizada)

### Opción 1: Con Maven directo

1. Levanta una instancia de PostgreSQL local (o usa Docker: `docker run -p 5432:5432 -e POSTGRES_PASSWORD=admin123 -e POSTGRES_DB=techstore postgres`).
2. Ajusta `src/main/resources/application.properties` con tus credenciales de conexión si es necesario.
3. Ejecuta:
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```
4. La API queda disponible en `http://localhost:8080`.

### Opción 2: Con Docker

```bash
docker build -t techstore-api .
docker run -p 8080:8080 techstore-api
```

## Despliegue en AWS

El proyecto está desplegado en una arquitectura nativa en la nube sobre AWS (ECS Fargate, ALB, RDS, SQS, Lambda y API Gateway). El detalle de la arquitectura, decisiones de escalabilidad y troubleshooting del despliegue se documenta en la siguiente sección.

---

## Despliegue en AWS — Arquitectura y Comportamiento de ECS Fargate

### Resumen del despliegue

El microservicio `techstore-api` se despliega como imagen Docker (build multi-stage, JRE alpino, usuario no-root) en Amazon ECR, orquestada por un Service de ECS Fargate con 2 tareas, expuesto a través de un Application Load Balancer y conectado a una base de datos RDS PostgreSQL dentro de la misma VPC.

### Escalabilidad de réplicas en caliente

A diferencia de un entorno local (por ejemplo, `docker-compose` corriendo un único contenedor en la máquina del desarrollador), ECS Fargate permite mantener múltiples réplicas del mismo servicio corriendo en paralelo, distribuidas en distintas zonas de disponibilidad (en este proyecto, `us-east-1a` y `us-east-1b`), sin necesidad de gestionar servidores subyacentes.

Se configuró una política de **Service Auto Scaling** de tipo *target tracking* sobre `ECSServiceAverageCPUUtilization`, con un valor objetivo de 60%, mínimo 1 y máximo 4 tareas. Cuando el promedio de CPU supera el 60% de forma sostenida, ECS lanza tareas nuevas automáticamente (scale-out), registrándolas en el Target Group solo tras pasar su health check, sin downtime. Cuando la carga baja, retira tareas gradualmente (scale-in) respetando un cooldown para evitar oscilaciones.

En un entorno local, replicar esto requeriría orquestar manualmente varios contenedores y un balanceador propio, sin un mecanismo nativo de autoescalado basado en métricas en tiempo real.

### Políticas de reinicio y tolerancia a fallos

ECS Fargate supervisa el estado de cada tarea mediante el health check del Target Group (`GET /`, 5 fallos consecutivos como umbral). Durante el desarrollo se observó este comportamiento en la práctica: al detectar una tarea `unhealthy`, ECS la detuvo automáticamente y lanzó una de reemplazo hasta restablecer el número deseado de réplicas, sin intervención manual.

Este ciclo de auto-reparación ("self-healing") contrasta con un contenedor local, donde un proceso caído permanece caído hasta reiniciarlo manualmente (salvo `restart: always` en `docker-compose`, un mecanismo más básico y sin verificación de salud real vía HTTP).

Se configuró además un **Health check grace period** de 180 segundos en el Service, necesario porque la aplicación tarda en completar su arranque (ver siguiente sección), y sin este margen ECS podía matar tareas que solo estaban demorándose, no fallando.

### Límites de CPU/Memoria: Fargate vs. entorno local

La Task Definition está configurada con los límites mínimos permitidos por la pauta del laboratorio:

- **CPU**: 0.25 vCPU
- **Memoria**: 0.5 GB RAM

Estos valores son sensiblemente más bajos que los recursos disponibles en una máquina de desarrollo local (que típicamente cuenta con varios núcleos de CPU y varios GB de RAM libres para Docker). Este límite tuvo un impacto real y medible durante el despliegue: en los logs de CloudWatch se observó que la aplicación Spring Boot tarda aproximadamente **100 segundos en completar su arranque** (inicialización de Hibernate, pool de conexiones HikariCP hacia RDS, contexto de Spring Security, etc.), un tiempo considerablemente mayor al que toma arrancar la misma aplicación en un entorno local con más recursos disponibles.

Esta limitación obligó a relajar los parámetros de health check para evitar falsos negativos: se aumentó el timeout de 5 a 30 segundos, el intervalo de 30 a 60 segundos, y el umbral de fallos consecutivos de 2 a 5, además del grace period de 180s ya mencionado.

En un entorno de producción real (fuera de las restricciones de AWS Academy), sería preferible aumentar los límites de CPU/memoria de la Task Definition para reducir el tiempo de arranque, en lugar de solo relajar la tolerancia del monitoreo.

### Redes y seguridad

El **Security Group del ALB** permite entrada pública en el puerto 80. El **Security Group de las tareas ECS** solo acepta tráfico en el puerto 8080 proveniente del Security Group del ALB, bloqueando accesos directos al microservicio. El **Security Group de RDS** solo acepta conexiones en el puerto 5432 desde el Security Group de las tareas ECS.

Las tareas Fargate usan IP pública (subnets públicas) para poder autenticar y descargar la imagen desde ECR; en producción, esto se resolvería con un NAT Gateway sobre subnets privadas o VPC Endpoints de interfaz para ECR.
