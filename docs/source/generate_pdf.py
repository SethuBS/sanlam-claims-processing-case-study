"""Generate the canonical claims-processing case-study PDF from its layout source."""

from __future__ import annotations

import argparse
import io
import json
from pathlib import Path

from pypdf import PdfReader, PdfWriter
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas


FONT_FILES = {
    "Sans": r"C:\Windows\Fonts\arial.ttf",
    "SansBold": r"C:\Windows\Fonts\arialbd.ttf",
    "SansItalic": r"C:\Windows\Fonts\ariali.ttf",
    "Mono": r"C:\Windows\Fonts\consola.ttf",
    "MonoBold": r"C:\Windows\Fonts\consolab.ttf",
}

NAVY = (0.043137, 0.25098, 0.372549)
TEAL = (0.047059, 0.345098, 0.454902)
TEXT = (0.113725, 0.156863, 0.188235)
MUTED = (0.431373, 0.529412, 0.584314)
LIGHT_BLUE = (0.929412, 0.956863, 0.976471)
BORDER = (0.78, 0.84, 0.87)


def register_fonts():
    for name, path in FONT_FILES.items():
        pdfmetrics.registerFont(TTFont(name, path))


def mapped_font(source_name: str) -> str:
    lowered = source_name.lower()
    if "mono" in lowered or "courier" in lowered:
        return "MonoBold" if "bold" in lowered else "Mono"
    if "bold" in lowered or "semi" in lowered:
        return "SansBold"
    if "italic" in lowered:
        return "SansItalic"
    return "Sans"


def rgb(value, default=(0, 0, 0)):
    if not value:
        return default
    if len(value) == 1:
        return tuple(value * 3)
    if len(value) >= 3:
        return tuple(value[:3])
    return default


def should_draw_run(page_number, run):
    text = run["text"].strip()
    if page_number in (18, 24) and text in (
        "13. Integration changes and migration",
        "17. Testing strategy",
    ):
        return run["font"] == "Helvetica-Bold"
    if page_number == 22:
        return run["baseline"] > 730 or run["baseline"] < 50
    return True


def draw_text(pdf, page, page_number):
    for run in page["text_runs"]:
        if not should_draw_run(page_number, run):
            continue
        text = run["text"]
        if not text:
            continue
        font = mapped_font(run["font"])
        size = max(float(run["size"]), 1.0)
        target_width = max(float(run["end_x"]) - float(run["x"]), 0.1)
        natural_width = pdfmetrics.stringWidth(text, font, size)
        horizontal_scale = 100.0 if natural_width <= 0 else max(20.0, min(300.0, target_width / natural_width * 100.0))

        text_object = pdf.beginText()
        text_object.setTextOrigin(run["x"], run["baseline"])
        text_object.setFont(font, size)
        text_object.setFillColorRGB(*rgb(run.get("color"), TEXT))
        text_object.setHorizScale(horizontal_scale)
        text_object.textOut(text)
        pdf.drawText(text_object)


def wrap_text(text: str, font: str, size: float, width: float):
    lines = []
    current = ""
    for word in text.split():
        candidate = word if not current else f"{current} {word}"
        if pdfmetrics.stringWidth(candidate, font, size) <= width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def draw_status_table(pdf):
    x0, x1, x2 = 48.2, 353.0, 547.0
    title_y = 270.0
    row_top = 254.0
    row_height = 30.0
    rows = [
        ("Claim intake, validation, idempotency and transactional outbox", "Implemented"),
        ("Client, policy and payment stubs", "Implemented"),
        ("Signed callbacks, replay protection and payment reconciliation", "Implemented"),
        ("AWS Step Functions, SQS, EventBridge and Cognito", "Proposed production architecture"),
        ("Complete enterprise audit history", "Future work"),
        ("Claims UI", "Outside prototype scope"),
    ]

    pdf.setFillColorRGB(*TEAL)
    pdf.setFont("SansBold", 11.2)
    pdf.drawString(x0, title_y, "Implementation status")
    pdf.setFillColorRGB(*MUTED)
    pdf.setFont("SansItalic", 6.8)
    pdf.drawString(x0, title_y - 10.0, "Prototype scope compared with the proposed production architecture")

    for index, (capability, status) in enumerate(rows):
        y = row_top - (index + 1) * row_height
        pdf.setFillColorRGB(*(LIGHT_BLUE if index % 2 == 0 else (1, 1, 1)))
        pdf.setStrokeColorRGB(*BORDER)
        pdf.setLineWidth(0.5)
        pdf.rect(x0, y, x2 - x0, row_height, fill=1, stroke=1)
        pdf.line(x1, y, x1, y + row_height)

        pdf.setFillColorRGB(*TEXT)
        for line_index, line in enumerate(wrap_text(capability, "Sans", 7.2, x1 - x0 - 14)):
            pdf.setFont("Sans", 7.2)
            pdf.drawString(x0 + 7, y + row_height - 11 - line_index * 8.5, line)
        for line_index, line in enumerate(wrap_text(status, "SansBold", 7.0, x2 - x1 - 14)):
            pdf.setFont("SansBold", 7.0)
            pdf.drawString(x1 + 7, y + row_height - 11 - line_index * 8.5, line)


def draw_code_disclaimer(pdf):
    pdf.setFillColorRGB(*MUTED)
    pdf.setFont("SansItalic", 6.4)
    pdf.drawString(
        48.2,
        744.0,
        "Illustrative production-oriented code - PaymentStatusEventHandler and ProcessedMessageRepository are not direct prototype classes.",
    )


def draw_code_block(pdf, title, x, header_y, lines, width):
    pdf.setFillColorRGB(0.282353, 0.337255, 0.372549)
    pdf.setFont("Mono", 7.0)
    pdf.drawString(x, header_y, title)
    label = "JAVA"
    pdf.drawString(x + width - pdfmetrics.stringWidth(label, "Mono", 7.0), header_y, label)

    pdf.setFillColorRGB(0.078431, 0.129412, 0.168627)
    pdf.setFont("Mono", 6.2)
    baseline = header_y - 22.0
    for line in lines:
        pdf.drawString(x, baseline, line)
        baseline -= 8.1


def draw_page_22_code(pdf):
    claim_lines = [
        "public final class Claim {",
        "    private ClaimStatus status;",
        "    private String paymentReference;",
        "    private Instant updatedAt;",
        "",
        "    public void markPaymentCompleted(",
        "            String receivedPaymentReference,",
        "            Instant completedAt) {",
        "        if (status == ClaimStatus.PAID",
        "                && receivedPaymentReference.equals(",
        "                    paymentReference)) {",
        "            return; // Duplicate callback is safe.",
        "        }",
        "",
        "        if (status != ClaimStatus.PAYMENT_PENDING) {",
        "            throw new InvalidClaimTransitionException(",
        "                status, ClaimStatus.PAID);",
        "        }",
        "",
        "        status = ClaimStatus.PAID;",
        "        paymentReference = receivedPaymentReference;",
        "        updatedAt = completedAt;",
        "",
        "        registerEvent(new ClaimPaid(",
        "            id, receivedPaymentReference,",
        "            completedAt));",
        "    }",
        "}",
    ]
    policy_lines = [
        "@Component",
        "@RequiredArgsConstructor",
        "final class PolicyManagerHttpAdapter",
        "        implements PolicyManagerPort {",
        "",
        "    private final RestClient policyManagerClient;",
        "",
        "    @Override",
        "    public PolicyEligibilityResult checkEligibility(",
        "            PolicyEligibilityCommand command) {",
        "        PolicyManagerResponse response =",
        "            policyManagerClient.post()",
        "                .uri(\"/api/v1/claim-eligibility-checks\")",
        "                .header(\"Idempotency-Key\",",
        "                    command.idempotencyKey())",
        "                .header(\"X-Correlation-Id\",",
        "                    command.correlationId().toString())",
        "                .body(PolicyManagerRequest.from(command))",
        "                .retrieve()",
        "                .body(PolicyManagerResponse.class);",
        "",
        "        if (response == null) {",
        "            throw new InvalidIntegrationResponseException(",
        "                \"Policy Manager returned an empty response\");",
        "        }",
        "",
        "        return response.toDomain();",
        "    }",
        "}",
    ]
    callback_lines = [
        "@Component",
        "@RequiredArgsConstructor",
        "final class PaymentStatusEventHandler {",
        "",
        "    private final ClaimRepository claimRepository;",
        "    private final ProcessedMessageRepository processedMessageRepository;",
        "",
        "    @Transactional",
        "    public void handle(PaymentStatusEvent event) {",
        "        if (processedMessageRepository.exists(",
        "                \"payment-status\", event.eventId())) {",
        "            return;",
        "        }",
        "",
        "        Claim claim = claimRepository.getRequired(event.claimId());",
        "        claim.apply(event);",
        "",
        "        claimRepository.save(claim);",
        "        processedMessageRepository.markProcessed(",
        "                \"payment-status\", event.eventId());",
        "    }",
        "}",
    ]

    draw_code_block(pdf, "Claim.java", 57.0, 725.0, claim_lines, 209.0)
    draw_code_block(pdf, "PolicyManagerHttpAdapter.java", 313.0, 725.0, policy_lines, 208.0)
    draw_code_block(pdf, "PaymentStatusEventHandler.java", 57.0, 327.0, callback_lines, 464.0)


def generate(source_path: Path, artwork_path: Path, output_path: Path):
    source = json.loads(source_path.read_text(encoding="utf-8"))
    register_fonts()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    first = source["pages"][0]
    overlay_stream = io.BytesIO()
    pdf = canvas.Canvas(overlay_stream, pagesize=(first["width"], first["height"]), pageCompression=1, invariant=1)
    pdf.setTitle(source["title"])
    pdf.setAuthor(source["author"])
    pdf.setSubject("Developer Case Study - Claims Processing Platform")

    for page_number, page in enumerate(source["pages"], start=1):
        pdf.setPageSize((page["width"], page["height"]))
        draw_text(pdf, page, page_number)
        if page_number == 2:
            draw_status_table(pdf)
        if page_number == 22:
            draw_code_disclaimer(pdf)
            draw_page_22_code(pdf)
        pdf.showPage()

    pdf.save()
    overlay_stream.seek(0)

    artwork = PdfReader(artwork_path)
    overlay = PdfReader(overlay_stream)
    writer = PdfWriter()
    writer.add_metadata({
        "/Title": source["title"],
        "/Author": source["author"],
        "/Subject": "Developer Case Study - Claims Processing Platform",
    })
    for artwork_page, overlay_page in zip(artwork.pages, overlay.pages, strict=True):
        artwork_page.merge_page(overlay_page)
        writer.add_page(artwork_page)
    with output_path.open("wb") as stream:
        writer.write(stream)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source_json", type=Path)
    parser.add_argument("artwork_pdf", type=Path)
    parser.add_argument("output_pdf", type=Path)
    args = parser.parse_args()
    generate(args.source_json, args.artwork_pdf, args.output_pdf)


if __name__ == "__main__":
    main()
