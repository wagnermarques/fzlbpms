#!/usr/bin/env python3
"""Deep PDF analysis for selected files without modifying the PDFs."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple


BASE_DIR = Path(__file__).resolve().parent
PDF_FILES = [
    "Cert Org Evt WorkshopDocker.pdf",
    "Cert Organi Evento Alunos que Entraram no Mercado de Trabalho.pdf",
    "Coordenação_Wagner França Marques.pdf",
    "Designicao para bancas.pdf",
]


def optional_import(module_name: str):
    try:
        return __import__(module_name)
    except Exception:
        return None


PyPDF2 = optional_import("PyPDF2")
pdfplumber = optional_import("pdfplumber")
pdf2image = optional_import("pdf2image")
pytesseract = optional_import("pytesseract")
PIL = optional_import("PIL")


@dataclass
class PDFReport:
    filename: str
    path: Path
    file_size_bytes: int = 0
    file_size_kb: float = 0.0
    page_count: Optional[int] = None
    metadata: Dict[str, Optional[str]] = field(default_factory=dict)
    text_extraction_success: bool = False
    text_sources: List[str] = field(default_factory=list)
    preview_lines: List[str] = field(default_factory=list)
    ocr_needed: bool = False
    best_group_match: str = ""
    group_reasoning: str = ""
    confidence: str = "Low"


def normalize_text(value: Optional[str]) -> str:
    if not value:
        return ""
    text = str(value).replace("\x00", " ")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def clean_pdf_date(value: Optional[str]) -> Optional[str]:
    raw = normalize_text(value)
    if not raw:
        return None
    if raw.startswith("D:"):
        raw = raw[2:]
    return raw


def meaningful_lines(text: str, limit: int = 3) -> List[str]:
    lines: List[str] = []
    for line in text.splitlines():
        cleaned = normalize_text(line)
        if len(cleaned) < 4:
            continue
        if re.fullmatch(r"[\W_]+", cleaned):
            continue
        if cleaned not in lines:
            lines.append(cleaned)
        if len(lines) >= limit:
            break
    return lines


def extract_with_pypdf2(path: Path) -> Tuple[Optional[int], Dict[str, Optional[str]], List[str]]:
    if not PyPDF2:
        return None, {}, []

    page_count: Optional[int] = None
    metadata: Dict[str, Optional[str]] = {}
    page_texts: List[str] = []

    with path.open("rb") as handle:
        reader = PyPDF2.PdfReader(handle)
        page_count = len(reader.pages)
        raw_meta = reader.metadata or {}
        metadata = {
            "title": normalize_text(raw_meta.get("/Title")),
            "author": normalize_text(raw_meta.get("/Author")),
            "subject": normalize_text(raw_meta.get("/Subject")),
            "creator": normalize_text(raw_meta.get("/Creator")),
            "producer": normalize_text(raw_meta.get("/Producer")),
            "creation_date": clean_pdf_date(raw_meta.get("/CreationDate")),
        }
        for page in reader.pages:
            try:
                page_texts.append(page.extract_text() or "")
            except Exception as exc:
                page_texts.append(f"[PyPDF2 extraction error: {exc}]")

    return page_count, metadata, page_texts


def extract_with_pdfplumber(path: Path) -> List[str]:
    if not pdfplumber:
        return []

    page_texts: List[str] = []
    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            try:
                page_texts.append(page.extract_text() or "")
            except Exception as exc:
                page_texts.append(f"[pdfplumber extraction error: {exc}]")
    return page_texts


def merge_page_texts(primary: List[str], secondary: List[str]) -> Tuple[List[str], List[str]]:
    merged: List[str] = []
    sources: List[str] = []
    max_pages = max(len(primary), len(secondary))

    for index in range(max_pages):
        first = primary[index] if index < len(primary) else ""
        second = secondary[index] if index < len(secondary) else ""

        if len(normalize_text(first)) >= len(normalize_text(second)):
            chosen = first
            source = "PyPDF2" if normalize_text(first) else ""
        else:
            chosen = second
            source = "pdfplumber" if normalize_text(second) else ""

        merged.append(chosen or "")
        if source:
            sources.append(source)

    return merged, sorted(set(sources))


def likely_needs_ocr(page_texts: List[str]) -> bool:
    combined = normalize_text(" ".join(page_texts))
    if not combined:
        return True
    return len(combined) < 120


def library_summary() -> Dict[str, bool]:
    return {
        "PyPDF2": PyPDF2 is not None,
        "pdfplumber": pdfplumber is not None,
        "pdf2image": pdf2image is not None,
        "PIL": PIL is not None,
        "pytesseract": pytesseract is not None,
    }


def classify_pdf(filename: str, report: PDFReport) -> Tuple[str, str, str]:
    haystack = " ".join(
        [
            filename,
            *[value or "" for value in report.metadata.values()],
            *report.preview_lines,
        ]
    ).lower()

    grupo3_rules = [
        ("filename/content mentions workshop or event", ["workshop", "evento", "mercado de trabalho", "organi evento"]),
        ("coordination/board duties fit professional experience roles", ["coordenação", "coordenacao", "banca", "bancas", "designicao", "designação"]),
        ("certificate-style activity evidence usually supports experience", ["cert", "certificado"]),
    ]
    grupo1_rules = [
        ("academic degree terms suggest titulação", ["titulação", "titulacao", "licenciatura", "graduação", "graduacao", "diploma"]),
        ("formal education/training terms suggest titulação", ["bacharelado", "mestrado", "doutorado", "especialização", "especializacao"]),
    ]

    grupo3_hits: List[str] = []
    grupo1_hits: List[str] = []

    for reason, keywords in grupo3_rules:
        if any(keyword in haystack for keyword in keywords):
            grupo3_hits.append(reason)

    for reason, keywords in grupo1_rules:
        if any(keyword in haystack for keyword in keywords):
            grupo1_hits.append(reason)

    if grupo3_hits and not grupo1_hits:
        confidence = "High" if len(grupo3_hits) >= 2 else "Medium"
        reasoning = "; ".join(grupo3_hits)
        return "Grupo 3 - Experiencia Profissional", reasoning, confidence

    if grupo1_hits and not grupo3_hits:
        confidence = "High" if len(grupo1_hits) >= 2 else "Medium"
        reasoning = "; ".join(grupo1_hits)
        return "Grupo1 - Titulacao", reasoning, confidence

    if grupo3_hits and grupo1_hits:
        if len(grupo3_hits) >= len(grupo1_hits):
            return (
                "Grupo 3 - Experiencia Profissional",
                "more professional-experience clues than titulação clues; "
                + "; ".join(grupo3_hits + grupo1_hits),
                "Medium",
            )
        return (
            "Grupo1 - Titulacao",
            "more titulação clues than professional-experience clues; "
            + "; ".join(grupo1_hits + grupo3_hits),
            "Medium",
        )

    return (
        "Grupo 3 - Experiencia Profissional",
        "defaulted to professional-experience because filenames describe activities/assignments rather than degrees",
        "Low",
    )


def analyze_pdf(path: Path) -> PDFReport:
    report = PDFReport(filename=path.name, path=path)
    report.file_size_bytes = path.stat().st_size
    report.file_size_kb = round(report.file_size_bytes / 1024, 2)

    pypdf2_pages, pypdf2_meta, pypdf2_text = extract_with_pypdf2(path)
    plumber_text = extract_with_pdfplumber(path)

    report.page_count = pypdf2_pages if pypdf2_pages is not None else (len(plumber_text) or None)
    report.metadata = {
        "title": pypdf2_meta.get("title") or None,
        "author": pypdf2_meta.get("author") or None,
        "subject": pypdf2_meta.get("subject") or None,
        "creator": pypdf2_meta.get("creator") or None,
        "producer": pypdf2_meta.get("producer") or None,
        "creation_date": pypdf2_meta.get("creation_date") or None,
    }

    merged_texts, sources = merge_page_texts(pypdf2_text, plumber_text)
    combined_text = "\n".join(text for text in merged_texts if text)

    report.text_sources = sources
    report.text_extraction_success = bool(normalize_text(combined_text))
    report.preview_lines = meaningful_lines(combined_text, limit=3)
    report.ocr_needed = likely_needs_ocr(merged_texts)

    report.best_group_match, report.group_reasoning, report.confidence = classify_pdf(path.name, report)
    return report


def format_metadata(report: PDFReport) -> str:
    ordered_keys = ["title", "author", "subject", "creator", "producer", "creation_date"]
    lines = []
    for key in ordered_keys:
        value = report.metadata.get(key) or "[not available]"
        lines.append(f"  - {key}: {value}")
    return "\n".join(lines)


def main() -> int:
    print("=" * 88)
    print("DEEP PDF ANALYSIS")
    print("=" * 88)
    print("Library availability:")
    for name, available in library_summary().items():
        print(f"  - {name}: {'available' if available else 'not available'}")

    for pdf_name in PDF_FILES:
        path = BASE_DIR / pdf_name
        print("\n" + "-" * 88)
        print(f"Filename: {pdf_name}")

        if not path.exists():
            print("Status: file not found")
            continue

        report = analyze_pdf(path)

        print(f"File size: {report.file_size_bytes} bytes ({report.file_size_kb} KB)")
        print(f"Page count: {report.page_count if report.page_count is not None else '[unknown]'}")
        print("Metadata:")
        print(format_metadata(report))
        print(
            "Embedded text extraction: "
            + ("Success" if report.text_extraction_success and not report.ocr_needed else "Fail/Minimal")
        )
        print(
            "Extraction sources: "
            + (", ".join(report.text_sources) if report.text_sources else "[none]")
        )

        if report.text_extraction_success and not report.ocr_needed and report.preview_lines:
            print("First meaningful lines:")
            for line in report.preview_lines:
                print(f"  - {line}")
        else:
            print("First meaningful lines:")
            print("  - [Image-based PDF - OCR needed]")

        print(f"Best group match: {report.best_group_match}")
        print(f"Reasoning: {report.group_reasoning}")
        print(f"Confidence: {report.confidence}")

    print("\nAnalysis complete.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
