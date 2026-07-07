package cl.techstore.api.controller;

import cl.techstore.api.dto.ProductoDTO;
import cl.techstore.api.model.Producto;
import cl.techstore.api.service.AuditPublisherService;
import cl.techstore.api.service.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private AuditPublisherService auditPublisherService;

    @GetMapping
    public ResponseEntity<List<Producto>> listar() {
        return ResponseEntity.ok(productoService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<Producto> crear(@RequestBody ProductoDTO dto, Authentication authentication) {
        Producto creado = productoService.crear(dto);
        auditPublisherService.publishAuditEvent("CREAR", creado.getId(), creado.getNombre(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Producto> modificar(@PathVariable Long id,
                                               @RequestBody ProductoDTO dto,
                                               Authentication authentication) {
        Producto modificado = productoService.modificar(id, dto);
        auditPublisherService.publishAuditEvent("MODIFICAR", modificado.getId(), modificado.getNombre(), authentication.getName());
        return ResponseEntity.ok(modificado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        Producto eliminado = productoService.eliminar(id);
        auditPublisherService.publishAuditEvent("ELIMINAR", eliminado.getId(), eliminado.getNombre(), authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
