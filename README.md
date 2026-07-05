# TechStore API 
Despliegue en AWS — Arquitectura y Comportamiento de ECS Fargate

Resumen del despliegue

El microservicio techstore-api se despliega como imagen Docker (build multi-stage, JRE alpino, usuario no-root) en Amazon ECR, orquestada por un Service de ECS Fargate con 2 tareas, expuesto a través de un Application Load Balancer y conectado a una base de datos RDS PostgreSQL dentro de la misma VPC.

Escalabilidad de réplicas en caliente

A diferencia de un entorno local (por ejemplo, docker-compose corriendo un único contenedor en la máquina del desarrollador), ECS Fargate permite mantener múltiples réplicas del mismo servicio corriendo en paralelo, distribuidas en distintas zonas de disponibilidad (en este proyecto, us-east-1a y us-east-1b), sin necesidad de gestionar servidores subyacentes.

Se configuró una política de Service Auto Scaling de tipo target tracking sobre ECSServiceAverageCPUUtilization, con un valor objetivo de 60%, mínimo 1 y máximo 4 tareas. Cuando el promedio de CPU supera el 60% de forma sostenida, ECS lanza tareas nuevas automáticamente (scale-out), registrándolas en el Target Group solo tras pasar su health check, sin downtime. Cuando la carga baja, retira tareas gradualmente (scale-in) respetando un cooldown para evitar oscilaciones.

En un entorno local, replicar esto requeriría orquestar manualmente varios contenedores y un balanceador propio, sin un mecanismo nativo de autoescalado basado en métricas en tiempo real.

Políticas de reinicio y tolerancia a fallos

ECS Fargate supervisa el estado de cada tarea mediante el health check del Target Group (GET /, 5 fallos consecutivos como umbral). Durante el desarrollo se observó este comportamiento en la práctica: al detectar una tarea unhealthy, ECS la detuvo automáticamente y lanzó una de reemplazo hasta restablecer el número deseado de réplicas, sin intervención manual.

Este ciclo de auto-reparación ("self-healing") contrasta con un contenedor local, donde un proceso caído permanece caído hasta reiniciarlo manualmente (salvo restart: always en docker-compose, un mecanismo más básico y sin verificación de salud real vía HTTP).

Se configuró además un Health check grace period de 180 segundos en el Service, necesario porque la aplicación tarda en completar su arranque (ver siguiente sección), y sin este margen ECS podía matar tareas que solo estaban demorándose, no fallando.

Límites de CPU/Memoria: Fargate vs. entorno local

La Task Definition está configurada con los límites mínimos permitidos por la pauta del laboratorio:


CPU: 0.25 vCPU
Memoria: 0.5 GB RAM


Estos valores son sensiblemente más bajos que los recursos disponibles en una máquina de desarrollo local (que típicamente cuenta con varios núcleos de CPU y varios GB de RAM libres para Docker). Este límite tuvo un impacto real y medible durante el despliegue: en los logs de CloudWatch se observó que la aplicación Spring Boot tarda aproximadamente 100 segundos en completar su arranque (inicialización de Hibernate, pool de conexiones HikariCP hacia RDS, contexto de Spring Security, etc.), un tiempo considerablemente mayor al que toma arrancar la misma aplicación en un entorno local con más recursos disponibles.

Esta limitación obligó a relajar los parámetros de health check para evitar falsos negativos: se aumentó el timeout de 5 a 30 segundos, el intervalo de 30 a 60 segundos, y el umbral de fallos consecutivos de 2 a 5, además del grace period de 180s ya mencionado.

En un entorno de producción real (fuera de las restricciones de AWS Academy), sería preferible aumentar los límites de CPU/memoria de la Task Definition para reducir el tiempo de arranque, en lugar de solo relajar la tolerancia del monitoreo.

Redes y seguridad

El Security Group del ALB permite entrada pública en el puerto 80. El Security Group de las tareas ECS solo acepta tráfico en el puerto 8080 proveniente del Security Group del ALB, bloqueando accesos directos al microservicio. El Security Group de RDS solo acepta conexiones en el puerto 5432 desde el Security Group de las tareas ECS.

Las tareas Fargate usan IP pública (subnets públicas) para poder autenticar y descargar la imagen desde ECR; en producción, esto se resolvería con un NAT Gateway sobre subnets privadas o VPC Endpoints de interfaz para ECR.
