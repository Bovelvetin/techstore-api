package cl.techstore.api.controller;

import cl.techstore.api.dto.ProductoDTO;
import cl.techstore.api.model.Producto;
import cl.techstore.api.service.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    @Autowired
    private ProductoService productoService;

    @GetMapping
    public ResponseEntity<List<Producto>> listar() {
        return ResponseEntity.ok(productoService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<Producto> crear(@RequestBody ProductoDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productoService.crear(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Producto> modificar(@PathVariable Long id,
                                               @RequestBody ProductoDTO dto) {
        return ResponseEntity.ok(productoService.modificar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}