package com.kvs.finance.service;

import com.kvs.core.exception.BadRequestException;
import com.kvs.core.exception.ConflictException;
import com.kvs.core.exception.ResourceNotFoundException;
import com.kvs.finance.dto.BounceChequeRequest;
import com.kvs.finance.dto.CounterCollectionRequest;
import com.kvs.finance.dto.DispatchReceiptRequest;
import com.kvs.finance.dto.ReceiptDispatchResponse;
import com.kvs.finance.dto.ReceiptResponse;
import org.springframework.beans.factory.annotation.Value;
import com.kvs.finance.entity.FeeAccount;
import com.kvs.finance.entity.FeePaymentReceipt;
import com.kvs.finance.entity.FeeReceiptAllocation;
import com.kvs.finance.entity.FeeReceiptDispatch;
import com.kvs.finance.entity.StudentFeeLedger;
import com.kvs.finance.enums.DispatchChannel;
import com.kvs.finance.enums.PaymentMode;
import com.kvs.finance.enums.PaymentStatus;
import com.kvs.finance.enums.ReceiptStatus;
import com.kvs.finance.repo.FeePaymentReceiptRepository;
import com.kvs.finance.repo.FeeReceiptDispatchRepository;
import com.kvs.finance.repo.StudentFeeLedgerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import com.kvs.core.notification.NotificationChannel;
import com.kvs.core.notification.NotificationDispatchException;
import com.kvs.core.notification.NotificationDispatcher;
import com.kvs.core.notification.NotificationMessage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fee counter collections and receipts.
 * Covers:
 * - US-04013: Log offline cash and cheque collections at the front-office fee counter (Trust Treasurer, Fee Cashier)
 * - US-04004: Generate auto-numbered PDF fee receipts and dispatch them via WhatsApp (Parent, Chief Accountant)
 * - US-04019: Flag bounced cheque transactions and reverse corresponding ledger credits (Auditor, School Principal)
 */
@Service
@Slf4j
@Transactional
public class FeeCollectionService {

    private static final Set<PaymentMode> COUNTER_MODES = EnumSet.of(PaymentMode.CASH, PaymentMode.CHEQUE, PaymentMode.DEMAND_DRAFT);
    private static final Set<PaymentMode> INSTRUMENT_MODES = EnumSet.of(PaymentMode.CHEQUE, PaymentMode.DEMAND_DRAFT);
    private static final Pattern INSTRUMENT_NUMBER = Pattern.compile("^\\d{6}$");
    private static final Pattern MOBILE = Pattern.compile("^\\+?\\d{10,15}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Autowired private FeeAccountService accountService;
    @Autowired private StudentFeeLedgerRepository ledgerRepository;
    @Autowired private FeePaymentReceiptRepository receiptRepository;
    @Autowired private FeeReceiptDispatchRepository dispatchRepository;
    @Autowired private ReceiptPdfWriter pdfWriter;
    @Autowired private FinanceAccess access;
    @Autowired private FinanceAuditService auditService;
    @Autowired private Clock clock;
    @Autowired private NotificationDispatcher notifications;
    @Autowired private SchoolMailClient schoolMail;
    // Fallback school details for receipt messages when the School profile cannot be read (kvs.school.* in application.yml)
    @Value("${kvs.school.name:School}") private String schoolName;
    @Value("${kvs.school.email:}") private String schoolEmail;
    @Value("${kvs.school.phone:}") private String schoolPhone;
    // Approved WhatsApp template for receipts (kvs.templates.receipt in application.yml)
    @Value("${kvs.templates.receipt:REPLACE_WITH_RECEIPT_TEMPLATE_NAME}") private String receiptTemplate;

    // ------------------------------------------------------------------ US-04013

    /** Records the payment and credits it to the oldest outstanding installments first. */
    public ReceiptResponse collectAtCounter(CounterCollectionRequest request) {
        if (!COUNTER_MODES.contains(request.getPaymentMode())) {
            throw new BadRequestException("paymentMode must be CASH, CHEQUE or DEMAND_DRAFT at the counter");
        }
        if (INSTRUMENT_MODES.contains(request.getPaymentMode())) {
            if (request.getInstrumentNumber() == null || !INSTRUMENT_NUMBER.matcher(request.getInstrumentNumber()).matches()) {
                throw new BadRequestException("instrumentNumber must be the 6-digit cheque / DD number");
            }
            if (request.getBankName() == null || request.getBankName().isBlank()) {
                throw new BadRequestException("bankName is required for a cheque or demand draft");
            }
            LocalDate today = LocalDate.now(clock);
            if (request.getInstrumentDate() != null && (request.getInstrumentDate().isAfter(today)
                    || request.getInstrumentDate().isBefore(today.minusMonths(3)))) {
                throw new BadRequestException("instrumentDate must be within the last 3 months and not post-dated");
            }
        }
        FeeAccount account = accountService.findAccount(request.getStudentId(), request.getAcademicYear());
        LocalDate today = LocalDate.now(clock);
        List<StudentFeeLedger> open = ledgerRepository.findByAccountAccountIdInOrderByDueDateAscInstallmentLabelAsc(List.of(account.getAccountId()))
                .stream().filter(l -> l.getPaymentStatus() != PaymentStatus.CANCELLED && l.outstanding().signum() > 0).toList();
        BigDecimal outstanding = FeeAccountService.sum(open, StudentFeeLedger::outstanding);
        if (outstanding.signum() == 0) {
            throw new ConflictException(account.getStudentName() + " has no outstanding fees for " + account.getAcademicYear());
        }
        if (request.getAmount().compareTo(outstanding) > 0) {
            throw new BadRequestException("amount " + request.getAmount() + " is more than the outstanding " + outstanding
                    + "; advance payments are not taken at the counter");
        }

        FeePaymentReceipt receipt = new FeePaymentReceipt();
        receipt.setTenantId(account.getTenantId());
        receipt.setBranchId(account.getBranchId());
        receipt.setAccount(account);
        receipt.setReceiptNumber(nextReceiptNumber(today));
        receipt.setTotalAmountPaid(request.getAmount());
        receipt.setPaymentMode(request.getPaymentMode());
        if (INSTRUMENT_MODES.contains(request.getPaymentMode())) {
            receipt.setInstrumentNumber(request.getInstrumentNumber());
            receipt.setBankName(request.getBankName().trim());
            receipt.setInstrumentDate(request.getInstrumentDate() == null ? today : request.getInstrumentDate());
        }
        receipt.setPayerName(request.getPayerName());
        receipt.setRemarks(request.getRemarks());
        receipt.setPaymentTimestamp(Instant.now(clock));
        receipt.setCollectedBy(access.currentUser());

        BigDecimal left = request.getAmount();
        for (StudentFeeLedger line : open) {
            if (left.signum() == 0) {
                break;
            }
            BigDecimal part = left.min(line.outstanding());
            line.setPaidAmount(line.getPaidAmount().add(part));
            line.refreshStatus(today);
            FeeReceiptAllocation allocation = new FeeReceiptAllocation();
            allocation.setReceipt(receipt);
            allocation.setLedger(line);
            allocation.setAmount(part);
            receipt.getAllocations().add(allocation);
            left = left.subtract(part);
        }
        receipt = receiptRepository.save(receipt);
        auditService.logSuccess("FeeReceipt:" + receipt.getReceiptId(), "FEE_COLLECTED_AT_COUNTER",
                receipt.getReceiptNumber() + " " + receipt.getPaymentMode() + " " + receipt.getTotalAmountPaid()
                        + " for " + account.getStudentUid() + " across " + receipt.getAllocations().size() + " installment(s)");
        log.info("Receipt {} issued for {} ({})", receipt.getReceiptNumber(), account.getStudentUid(), receipt.getTotalAmountPaid());
        return FinanceMapper.receipt(receipt);
    }

    /** RCPT-<year>-NNNNNN, numbered per calendar year across the platform (receipt numbers are globally unique). */
    private synchronized String nextReceiptNumber(LocalDate today) {
        String prefix = "RCPT-" + today.getYear() + "-";
        String last = receiptRepository.findLastNumber(prefix);
        long next = last == null ? 1 : Long.parseLong(last.substring(prefix.length())) + 1;
        return prefix + String.format("%06d", next);
    }

    // ------------------------------------------------------------------ US-04004

    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(UUID receiptId) {
        return FinanceMapper.receipt(findReceipt(receiptId));
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> listForStudent(UUID studentId, String academicYear) {
        FeeAccount account = accountService.findAccount(studentId, academicYear);
        return receiptRepository.findByAccountAccountIdInOrderByPaymentTimestampDesc(List.of(account.getAccountId()))
                .stream().map(FinanceMapper::receipt).toList();
    }

    /** Receipts of a branch issued between two dates (inclusive), newest first: the day-book on the Fees page. */
    @Transactional(readOnly = true)
    public List<ReceiptResponse> listForBranch(String tenantId, String branchId, LocalDate from, LocalDate to) {
        String tenant = access.tenantFor(tenantId);
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end)) {
            throw new BadRequestException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(start, end) > 366) {
            throw new BadRequestException("The period must be at most one year");
        }
        return receiptRepository.findBranchReceipts(tenant, branchId,
                        start.atStartOfDay(clock.getZone()).toInstant(), end.plusDays(1).atStartOfDay(clock.getZone()).toInstant())
                .stream().map(FinanceMapper::receipt).toList();
    }

    @Transactional(readOnly = true)
    public byte[] receiptPdf(UUID receiptId) {
        return pdfWriter.write(findReceipt(receiptId), clock.getZone());
    }

    @Transactional(readOnly = true)
    public String receiptNumber(UUID receiptId) {
        return findReceipt(receiptId).getReceiptNumber();
    }

    /**
     * Sends the receipt. E-mail goes through Settings → Integrations (the Finance mailbox, else the school's main account);
     * WhatsApp through the shared notification layer; SMS has no sender yet, so it stays QUEUED.
     * Returns the status to store: SENT, FAILED or QUEUED (and sets the dispatch's status detail for e-mail).
     */
    private String deliver(FeeReceiptDispatch dispatch, FeePaymentReceipt receipt) {
        if (dispatch.getChannel() == DispatchChannel.EMAIL) {
            NotificationMessage m = receiptEmail(dispatch.getRecipient(), receipt);
            SchoolMailClient.Outcome o = schoolMail.send(receipt.getTenantId(), m.recipient(), m.subject(), m.body(),
                    m.attachments().stream().map(a -> new SchoolMailClient.Attachment(a.filename(), a.contentType(), a.content())).toList(),
                    "receipt " + receipt.getReceiptNumber());
            dispatch.setStatusDetail(o.detail());
            return o.status().equals("SENT") ? "SENT" : "FAILED";
        }
        NotificationChannel channel = switch (dispatch.getChannel()) {
            case WHATSAPP -> NotificationChannel.WHATSAPP;
            case EMAIL -> NotificationChannel.EMAIL;
            default -> null;
        };
        if (channel == null) {
            return "QUEUED";
        }
        try {
            NotificationMessage message = channel == NotificationChannel.EMAIL
                    ? receiptEmail(dispatch.getRecipient(), receipt)
                    : NotificationMessage.template(dispatch.getRecipient(), receiptTemplate, receiptParams(receipt));
            notifications.send(channel, message);
            return "SENT";
        } catch (NotificationDispatchException e) {
            log.warn("Receipt {} not delivered via {}: {}", receipt.getReceiptNumber(), channel, e.getMessage());
            return "FAILED";
        }
    }

    /** The school's name, email and phone: Settings → School profile, else kvs.school.* in application.yml */
    private SchoolMailClient.SchoolProfile school(FeePaymentReceipt receipt) {
        SchoolMailClient.SchoolProfile p = schoolMail.schoolProfile(receipt.getTenantId());
        return p != null ? p : new SchoolMailClient.SchoolProfile(schoolName, schoolEmail, schoolPhone);
    }

    /** Subject "<School> | <Student>  <Class>, <Section> I Payment Receipt Copy.", with the receipt PDF attached. */
    private NotificationMessage receiptEmail(String recipient, FeePaymentReceipt receipt) {
        FeeAccount account = receipt.getAccount();
        SchoolMailClient.SchoolProfile school = school(receipt);
        String classSection = Objects.toString(account.getGradeName(), "") + ", " + Objects.toString(account.getSectionName(), "");
        String subject = school.name() + " | " + account.getStudentName() + "  " + classSection + " I Payment Receipt Copy.";
        String body = "Dear Parent/Guardian,\n\n"
                + "Please find attached the payment receipt copy for " + account.getStudentName() + ".\n\n"
                + "Receipt number: " + receipt.getReceiptNumber() + "\n"
                + "Amount paid: INR " + receipt.getTotalAmountPaid().toPlainString() + "\n"
                + "Date: " + receiptDate(receipt) + "\n\n"
                + "Regards,\n"
                + school.name() + "\n"
                + (school.email().isBlank() ? "" : "Email: " + school.email() + "\n")
                + (school.phone().isBlank() ? "" : "Phone: " + school.phone() + "\n");
        byte[] pdf = pdfWriter.write(receipt, clock.getZone());
        return new NotificationMessage(recipient, subject, body, List.of(
                new NotificationMessage.Attachment("Receipt-" + receipt.getReceiptNumber() + ".pdf", "application/pdf", pdf)));
    }

    /** Values for the approved WhatsApp receipt template, in placeholder order: school, student, receipt no., amount, date. */
    private List<String> receiptParams(FeePaymentReceipt receipt) {
        return List.of(school(receipt).name(), receipt.getAccount().getStudentName(), receipt.getReceiptNumber(),
                "INR " + receipt.getTotalAmountPaid().toPlainString(), receiptDate(receipt));
    }

    private String receiptDate(FeePaymentReceipt receipt) {
        return receipt.getPaymentTimestamp().atZone(clock.getZone()).toLocalDate().toString();
    }

    /** Sends the receipt by WhatsApp, e-mail or SMS (SMS is only queued). */
    public ReceiptDispatchResponse dispatch(UUID receiptId, DispatchReceiptRequest request) {
        FeePaymentReceipt receipt = findReceipt(receiptId);
        String recipient = request.getRecipient().trim();
        boolean valid = request.getChannel() == DispatchChannel.EMAIL
                ? EMAIL.matcher(recipient).matches()
                : MOBILE.matcher(recipient.replace(" ", "")).matches();
        if (!valid) {
            throw new BadRequestException(request.getChannel() == DispatchChannel.EMAIL
                    ? "recipient must be an e-mail address" : "recipient must be a mobile number such as +919876543210");
        }
        if (receipt.getReceiptStatus() == ReceiptStatus.BOUNCED) {
            throw new ConflictException("Receipt " + receipt.getReceiptNumber() + " is void (cheque bounced)");
        }
        FeeReceiptDispatch dispatch = new FeeReceiptDispatch();
        dispatch.setReceipt(receipt);
        dispatch.setChannel(request.getChannel());
        dispatch.setRecipient(request.getChannel() == DispatchChannel.EMAIL ? recipient : recipient.replace(" ", ""));
        dispatch.setRequestedBy(access.currentUser());
        dispatch.setRequestedAt(Instant.now(clock));
        dispatch.setStatus(deliver(dispatch, receipt));
        dispatch = dispatchRepository.save(dispatch);
        auditService.logSuccess("FeeReceipt:" + receiptId, "FEE_RECEIPT_DISPATCH_QUEUED",
                receipt.getReceiptNumber() + " via " + dispatch.getChannel() + " to " + dispatch.getRecipient());
        return ReceiptDispatchResponse.builder()
                .dispatchId(dispatch.getDispatchId()).receiptId(receiptId).receiptNumber(receipt.getReceiptNumber())
                .channel(dispatch.getChannel()).recipient(dispatch.getRecipient()).status(dispatch.getStatus()).statusDetail(dispatch.getStatusDetail())
                .requestedBy(dispatch.getRequestedBy()).requestedAt(dispatch.getRequestedAt())
                .build();
    }

    // ------------------------------------------------------------------ US-04019

    /** Voids a cheque / DD receipt, takes its credit back off the installments and adds any bank charge to the dues. */
    public ReceiptResponse markBounced(UUID receiptId, BounceChequeRequest request) {
        FeePaymentReceipt receipt = findReceipt(receiptId);
        if (!INSTRUMENT_MODES.contains(receipt.getPaymentMode())) {
            throw new ConflictException("Only cheque and demand draft receipts can bounce; " + receipt.getReceiptNumber()
                    + " was paid by " + receipt.getPaymentMode());
        }
        if (receipt.getReceiptStatus() == ReceiptStatus.BOUNCED) {
            throw new ConflictException("Receipt " + receipt.getReceiptNumber() + " is already marked as bounced");
        }
        LocalDate today = LocalDate.now(clock);
        BigDecimal charge = request.getBounceCharge() == null ? BigDecimal.ZERO : request.getBounceCharge();
        StudentFeeLedger earliest = null;
        for (FeeReceiptAllocation allocation : receipt.getAllocations()) {
            StudentFeeLedger line = allocation.getLedger();
            line.setPaidAmount(line.getPaidAmount().subtract(allocation.getAmount()).max(BigDecimal.ZERO));
            if (earliest == null || line.getDueDate().isBefore(earliest.getDueDate())) {
                earliest = line;
            }
        }
        if (earliest != null && charge.signum() > 0) {
            earliest.setLateFineAmount(earliest.getLateFineAmount().add(charge));
        }
        receipt.getAllocations().forEach(x -> x.getLedger().refreshStatus(today));
        receipt.setReceiptStatus(ReceiptStatus.BOUNCED);
        receipt.setBouncedAt(Instant.now(clock));
        receipt.setBouncedBy(access.currentUser());
        receipt.setBounceReason(request.getReason().trim());
        receipt.setBounceCharge(charge);
        auditService.logSuccess("FeeReceipt:" + receiptId, "FEE_CHEQUE_BOUNCED",
                receipt.getReceiptNumber() + " cheque " + receipt.getInstrumentNumber() + " reversed " + receipt.getTotalAmountPaid()
                        + ", charge " + charge + ": " + receipt.getBounceReason());
        return FinanceMapper.receipt(receipt);
    }

    private FeePaymentReceipt findReceipt(UUID receiptId) {
        FeePaymentReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("FeeReceipt", "receiptId", receiptId));
        if (!access.canSee(receipt.getAccount())) {
            throw new ResourceNotFoundException("FeeReceipt", "receiptId", receiptId);
        }
        return receipt;
    }
}
