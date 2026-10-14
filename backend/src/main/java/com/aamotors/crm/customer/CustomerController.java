package com.aamotors.crm.customer;

import com.aamotors.crm.common.error.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CustomerDtos.CustomerView> search(@RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.search(q, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/lookup")
    public CustomerDtos.CustomerView lookup(@RequestParam String phone) {
        return service.lookupByPhone(phone).orElseThrow(() -> ApiException.notFound("Customer not found"));
    }

    @GetMapping("/{id}")
    public CustomerDtos.CustomerView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDtos.CustomerView create(@Valid @RequestBody CustomerDtos.CreateCustomerRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public CustomerDtos.CustomerView update(@PathVariable UUID id, @Valid @RequestBody CustomerDtos.UpdateCustomerRequest req) {
        return service.update(id, req);
    }

    @PutMapping("/{id}/consents")
    public CustomerDtos.CustomerView consents(@PathVariable UUID id, @Valid @RequestBody CustomerDtos.UpdateConsentsRequest req) {
        return service.updateConsents(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
