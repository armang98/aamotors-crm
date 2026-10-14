package com.aamotors.crm.customer;

import com.aamotors.crm.audit.AuditService;
import com.aamotors.crm.common.error.ApiException;
import com.aamotors.crm.common.phone.PhoneNumberNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToLongFunction;

@Service
public class CustomerService {

    private static final String NO_MATCH = "#";

    private final CustomerRepository customers;
    private final AuditService audit;
    private final Clock clock;
    /** Supplied by the vehicle module to avoid a circular dependency on its repository. */
    private volatile ToLongFunction<UUID> vehicleCounter = id -> 0L;

    public CustomerService(CustomerRepository customers, AuditService audit, Clock clock) {
        this.customers = customers;
        this.audit = audit;
        this.clock = clock;
    }

    public void registerVehicleCounter(ToLongFunction<UUID> counter) {
        this.vehicleCounter = counter;
    }

    @Transactional(readOnly = true)
    public Page<CustomerDtos.CustomerView> search(String q, Pageable pageable) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            return customers.findAll(pageable).map(this::toView);
        }
        // A fragment that can never match disables the phone side of the OR without binding a null parameter.
        String phoneFragment = PhoneNumberNormalizer.searchFragment(query).orElse(NO_MATCH);
        return customers.search(phoneFragment, query.toLowerCase(), pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public CustomerDtos.CustomerView get(UUID id) {
        return toView(require(id));
    }

    @Transactional(readOnly = true)
    public Optional<CustomerDtos.CustomerView> lookupByPhone(String rawPhone) {
        return PhoneNumberNormalizer.normalize(rawPhone).flatMap(customers::findByPhone).map(this::toView);
    }

    @Transactional
    public CustomerDtos.CustomerView create(CustomerDtos.CreateCustomerRequest req) {
        Customer c = createEntity(req.fullName(), req.phone(), req.preferredLanguage(), req.notes());
        return toView(c);
    }

    /** Creates a customer, or returns the existing one with the same phone. Used by vehicle creation. */
    @Transactional
    public Customer findOrCreate(String fullName, String rawPhone, Language language) {
        String phone = PhoneNumberNormalizer.normalizeOrThrow(rawPhone);
        return customers.findByPhone(phone).orElseGet(() -> createEntity(fullName, phone, language, null));
    }

    @Transactional
    public CustomerDtos.CustomerView update(UUID id, CustomerDtos.UpdateCustomerRequest req) {
        Customer c = require(id);
        String phone = PhoneNumberNormalizer.normalizeOrThrow(req.phone());
        customers.findByPhone(phone).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw ApiException.conflict("customer.phoneExists", "Another customer already uses this phone number");
        });
        Map<String, Object> before = snapshot(c);
        c.setFullName(req.fullName().trim());
        c.setPhone(phone);
        c.setPreferredLanguage(req.preferredLanguage() == null ? c.getPreferredLanguage() : req.preferredLanguage());
        c.setNotes(blankToNull(req.notes()));
        c.setMarketingOptOut(req.marketingOptOut());
        audit.record("CUSTOMER_UPDATED", "customer", id.toString(), Map.of("before", before, "after", snapshot(c)));
        return toView(c);
    }

    @Transactional
    public CustomerDtos.CustomerView updateConsents(UUID id, CustomerDtos.UpdateConsentsRequest req) {
        Customer c = require(id);
        Instant now = Instant.now(clock);
        for (CustomerDtos.ConsentInput in : req.consents()) {
            CustomerConsent consent = c.consentFor(in.channel());
            if (consent == null) {
                consent = new CustomerConsent(c, in.channel());
                c.getConsents().add(consent);
            }
            consent.apply(in.granted(), in.source(), now);
        }
        audit.record("CUSTOMER_CONSENTS_UPDATED", "customer", id.toString(),
                Map.of("consents", req.consents().stream().map(ci -> ci.channel() + "=" + ci.granted()).toList()));
        return toView(c);
    }

    @Transactional
    public void delete(UUID id) {
        Customer c = require(id);
        if (vehicleCounter.applyAsLong(id) > 0) {
            throw ApiException.conflict("customer.hasVehicles", "Customer still has vehicles");
        }
        customers.delete(c);
        audit.record("CUSTOMER_DELETED", "customer", id.toString(), snapshot(c));
    }

    @Transactional(readOnly = true)
    public Customer require(UUID id) {
        return customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer not found"));
    }

    private Customer createEntity(String fullName, String rawPhone, Language language, String notes) {
        String phone = PhoneNumberNormalizer.normalizeOrThrow(rawPhone);
        if (customers.findByPhone(phone).isPresent()) {
            throw ApiException.conflict("customer.phoneExists", "A customer with this phone number already exists");
        }
        Customer c = new Customer(fullName.trim(), phone, language);
        c.setNotes(blankToNull(notes));
        customers.save(c);
        audit.record("CUSTOMER_CREATED", "customer", c.getId().toString(), snapshot(c));
        return c;
    }

    public CustomerDtos.CustomerView toView(Customer c) {
        var consents = c.getConsents().stream()
                .sorted(Comparator.comparing(CustomerConsent::getChannel))
                .map(x -> new CustomerDtos.ConsentView(x.getChannel(), x.isGranted(), x.getGrantedAt(), x.getRevokedAt(), x.getSource()))
                .toList();
        return new CustomerDtos.CustomerView(c.getId(), c.getFullName(), c.getPhone(), c.getPreferredLanguage(), c.getNotes(),
                c.isMarketingOptOut(), consents, vehicleCounter.applyAsLong(c.getId()), c.getCreatedAt(), c.getUpdatedAt());
    }

    private static Map<String, Object> snapshot(Customer c) {
        var m = new java.util.LinkedHashMap<String, Object>();
        m.put("fullName", c.getFullName());
        m.put("phone", PhoneNumberNormalizer.mask(c.getPhone()));
        m.put("preferredLanguage", c.getPreferredLanguage().name());
        m.put("marketingOptOut", c.isMarketingOptOut());
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
