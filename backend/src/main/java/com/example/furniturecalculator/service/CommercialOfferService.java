package com.example.furniturecalculator.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.example.furniturecalculator.dto.OrderLineExportRequestDto;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

// Коммерческое предложение (КП) в PDF по позициям заказа (см. change add-commercial-offer-pdf-export,
// commercial-offer-export). Оформление по образцу заказа HAUSDOORS: логотип, строки «Номер заказа»/«Дата и
// время заказа» с тонкими линиями, на странице каждой позиции — изображение двери слева и характеристики
// справа, ниже таблица комплектации и крупная «Итоговая цена». Расчёт компонентов и цен — тот же, что у
// выгрузки заказа в Excel (SpecificationExportService.resolveConfigurationBreakdown), дилерские цены в
// документ не попадают. Шрифт (Roboto, Apache 2.0) лежит в ресурсах и встраивается в PDF — без зависимости
// от шрифтов ОС и AWT.
@Service
public class CommercialOfferService {

    private static final Logger log = LoggerFactory.getLogger(CommercialOfferService.class);

    private static final String RESOURCES = "commercial-offer/";
    private static final java.awt.Color DARK = new java.awt.Color(0x1F, 0x1F, 0x1F);
    private static final java.awt.Color MUTED = new java.awt.Color(0x6B, 0x6B, 0x6B);
    private static final java.awt.Color LINE = new java.awt.Color(0xCF, 0xCF, 0xCF);
    private static final float MARGIN = 28f;
    private static final float TOP_MARGIN = 150f;
    private static final float MAX_DOOR_IMAGE_HEIGHT = 330f;
    private static final float TABLE_FONT = 8.5f;
    private static final float LOGO_WIDTH = 190f;
    private static final DateTimeFormatter NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String[] COLUMNS = {"Наименование", "Размер", "Цвет", "К-во", "Цена", "Стоимость"};

    private final SpecificationExportService specificationExportService;
    private final DoorImageCatalog doorImageCatalog;

    public CommercialOfferService(SpecificationExportService specificationExportService, DoorImageCatalog doorImageCatalog) {
        this.specificationExportService = specificationExportService;
        this.doorImageCatalog = doorImageCatalog;
    }

    // number — номер заказа вида yyMMddHHmm (момент формирования), он же в шапке страниц и в имени файла.
    public record Offer(String number, byte[] content) {
    }

    public Offer export(List<OrderLineExportRequestDto> lines) {
        OrderExportService.validateLines(lines);
        // Расчёт всех позиций — до начала записи PDF: невалидная позиция (404/400) не должна оставлять
        // частично сформированный файл.
        List<OfferLine> offerLines = lines.stream().map(this::resolve).toList();
        LocalDateTime now = LocalDateTime.now();
        String number = now.format(NUMBER_FORMAT);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Fonts fonts = loadFonts();
            Document document = new Document(PageSize.A4, MARGIN, MARGIN, TOP_MARGIN, MARGIN);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageHeader(fonts, loadResourceBytes("logo-hausdoors.png"), number, now.format(DATE_FORMAT)));
            document.open();
            writePositions(document, offerLines, fonts);
            document.close();
            return new Offer(number, out.toByteArray());
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Не удалось сформировать коммерческое предложение", e);
        }
    }

    private OfferLine resolve(OrderLineExportRequestDto line) {
        SpecificationExportService.ConfigurationBreakdown breakdown =
                specificationExportService.resolveConfigurationBreakdown(line.specification());
        Optional<Resource> image = doorImageCatalog.find(breakdown.collectionName(), breakdown.displayName(), breakdown.leafColourName());
        return new OfferLine(line, breakdown, image.orElse(null));
    }

    private record OfferLine(
            OrderLineExportRequestDto line, SpecificationExportService.ConfigurationBreakdown breakdown, Resource image) {

        BigDecimal sum() {
            return breakdown.totals().retail().multiply(BigDecimal.valueOf(line.quantity()));
        }
    }

    private record Fonts(BaseFont regular, BaseFont bold) {

        Font regular(float size) {
            return new Font(regular, size, Font.NORMAL, DARK);
        }

        Font bold(float size) {
            return new Font(bold, size, Font.NORMAL, DARK);
        }

        Font muted(float size) {
            return new Font(regular, size, Font.NORMAL, MUTED);
        }
    }

    private Fonts loadFonts() throws IOException, DocumentException {
        return new Fonts(loadFont("Roboto-Regular.ttf"), loadFont("Roboto-Bold.ttf"));
    }

    private BaseFont loadFont(String file) throws IOException, DocumentException {
        return BaseFont.createFont(file, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, loadResourceBytes("fonts/" + file), null);
    }

    private byte[] loadResourceBytes(String file) throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCES + file).getInputStream()) {
            return in.readAllBytes();
        }
    }

    // --- Страницы позиций: каждая позиция начинается с новой страницы ---

    private void writePositions(Document document, List<OfferLine> lines, Fonts fonts) throws DocumentException {
        BigDecimal orderTotal = BigDecimal.ZERO;
        int position = 1;
        for (OfferLine offerLine : lines) {
            if (position > 1) {
                document.newPage();
            }
            writePosition(document, offerLine, position, lines.size(), fonts);
            orderTotal = orderTotal.add(offerLine.sum());
            position++;
        }
        if (lines.size() > 1) {
            document.add(totalTable("Итого по заказу", orderTotal, fonts, 16));
        }
    }

    private void writePosition(Document document, OfferLine offerLine, int position, int count, Fonts fonts) throws DocumentException {
        OrderLineExportRequestDto line = offerLine.line();
        SpecificationExportService.ConfigurationBreakdown breakdown = offerLine.breakdown();

        PdfPTable top = new PdfPTable(new float[] {50, 50});
        top.setWidthPercentage(100);
        top.setKeepTogether(true);
        top.addCell(imageCell(offerLine.image(), fonts));

        PdfPTable specs = new PdfPTable(new float[] {34, 66});
        specs.setWidthPercentage(100);
        specs.setExtendLastRow(false);
        if (count > 1) {
            addSpecRow(specs, "Позиция", position + " из " + count, fonts);
        }
        addSpecRow(specs, "Модель", configurationText(line), fonts);
        if (breakdown.leafColourName() != null) {
            addSpecRow(specs, "Цвет", breakdown.leafColourName(), fonts);
        }
        String size = doorSize(breakdown.dimensionsLabel());
        if (!size.isEmpty()) {
            addSpecRow(specs, "Размер", size, fonts);
        }
        addSpecRow(specs, "Количество", line.quantity() + " шт.", fonts);
        PdfPCell specsCell = new PdfPCell();
        specsCell.addElement(specs);
        specsCell.setBorder(Rectangle.NO_BORDER);
        specsCell.setPaddingLeft(14);
        specsCell.setVerticalAlignment(Element.ALIGN_TOP);
        top.addCell(specsCell);
        document.add(top);

        PdfPTable table = new PdfPTable(new float[] {31, 16, 17, 7, 14.5f, 14.5f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(14);
        table.setHeaderRows(1);
        table.setSplitLate(false);
        for (int i = 0; i < COLUMNS.length; i++) {
            table.addCell(tableCell(COLUMNS[i], fonts.muted(8.5f), i == 0 ? Element.ALIGN_LEFT : i <= 3 ? Element.ALIGN_CENTER : Element.ALIGN_RIGHT));
        }
        boolean first = true;
        for (SpecificationExportService.DetailRow row : breakdown.detailRows()) {
            Font nameFont = first ? fonts.bold(TABLE_FONT) : fonts.regular(TABLE_FONT);
            table.addCell(tableCell(rowName(row), nameFont, Element.ALIGN_LEFT));
            table.addCell(tableCell(first ? size : nullToEmpty(row.size()), fonts.regular(TABLE_FONT), Element.ALIGN_CENTER));
            table.addCell(tableCell(nullToEmpty(row.colour()), fonts.regular(TABLE_FONT), Element.ALIGN_CENTER));
            table.addCell(tableCell(String.valueOf(row.quantity()), fonts.regular(TABLE_FONT), Element.ALIGN_CENTER));
            String price = "";
            String sum = "";
            if (row.priceApplicable()) {
                price = row.priced() ? money(row.retailPrice()) : "—";
                sum = row.priced() ? money(row.retailSum()) : "—";
            }
            table.addCell(tableCell(price, fonts.regular(TABLE_FONT), Element.ALIGN_RIGHT));
            table.addCell(tableCell(sum, fonts.regular(TABLE_FONT), Element.ALIGN_RIGHT));
            first = false;
        }
        document.add(table);

        document.add(totalTable("Итоговая цена:", offerLine.sum(), fonts, 18));
    }

    private PdfPCell imageCell(Resource resource, Fonts fonts) {
        Image door = loadDoorImage(resource);
        PdfPCell cell = door != null ? new PdfPCell(door, true) : new PdfPCell(new Phrase("Изображение недоступно", fonts.muted(9)));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(MAX_DOOR_IMAGE_HEIGHT);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        cell.setPadding(0);
        if (door == null) {
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        }
        return cell;
    }

    // Строка «Название: значение» с тонкой линией снизу — как строки характеристик образца.
    private void addSpecRow(PdfPTable table, String label, String value, Fonts fonts) {
        table.addCell(lineCell(label, fonts.muted(10), 7));
        table.addCell(lineCell(value, fonts.bold(10), 7));
    }

    private PdfPCell lineCell(String text, Font font, float padding) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setBorderWidth(0.7f);
        cell.setPaddingTop(padding);
        cell.setPaddingBottom(padding);
        cell.setPaddingLeft(0);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private PdfPCell tableCell(String text, Font font, int align) {
        PdfPCell cell = lineCell(text, font, 5);
        cell.setHorizontalAlignment(align);
        cell.setPaddingRight(4);
        return cell;
    }

    private PdfPTable totalTable(String label, BigDecimal value, Fonts fonts, float valueSize) {
        PdfPTable table = new PdfPTable(new float[] {50, 50});
        table.setWidthPercentage(100);
        table.setSpacingBefore(12);
        table.setKeepTogether(true);
        table.addCell(lineCell(label, fonts.bold(11), 10));
        table.addCell(lineCell(money(value), fonts.bold(valueSize), 8));
        return table;
    }

    // «Наименование — ТЕГ1, ТЕГ2» — тот же формат, что и в столбце «Конфигурация» Excel-выгрузки заказа.
    private String configurationText(OrderLineExportRequestDto line) {
        List<String> tags = line.attributeTags();
        if (tags == null || tags.isEmpty()) {
            return line.displayName();
        }
        return line.displayName() + " — " + String.join(", ", tags);
    }

    // Строка полотна — только название (размер и цвет вынесены в отдельные столбцы), остальные — «Элемент:
    // название цвет», чтобы «Короб»/«Наличник» отличались у типов с одинаковыми названиями.
    private String rowName(SpecificationExportService.DetailRow row) {
        StringBuilder text = new StringBuilder();
        if (!"Полотно".equals(row.element())) {
            text.append(row.element()).append(": ");
        }
        text.append(row.name());
        return text.toString();
    }

    // dimensionsLabel — «длина × высота × толщина» (длина — ширина полотна); в КП — «ширина*высота*толщина»
    // (900*2200*44, мм), толщина — только если выбрана.
    private String doorSize(String dimensionsLabel) {
        return dimensionsLabel == null ? "" : dimensionsLabel.replace(" × ", "*");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String money(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('\u00A0');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.##", symbols);
        return format.format(value) + "\u00A0₽";
    }

    // Изображение двери: JPEG/WebP декодируются через ImageIO и перекодируются в JPEG. Любая ошибка чтения —
    // страница без изображения (КП не должно падать из-за одного файла).
    private Image loadDoorImage(Resource resource) {
        if (resource == null) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                log.warn("Нет декодера для изображения двери {}", resource.getFilename());
                return null;
            }
            return Image.getInstance(toJpeg(image));
        } catch (IOException | DocumentException e) {
            log.warn("Не удалось прочитать изображение двери {}: {}", resource.getFilename(), e.getMessage());
            return null;
        }
    }

    private byte[] toJpeg(BufferedImage source) throws IOException {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(rgb, "jpg", out);
            return out.toByteArray();
        }
    }

    // Шапка каждой страницы: логотип HAUSDOORS и строки «Номер заказа»/«Дата и время заказа» с линиями.
    private static final class PageHeader extends PdfPageEventHelper {

        private final Fonts fonts;
        private final byte[] logoBytes;
        private final String number;
        private final String dateTime;

        PageHeader(Fonts fonts, byte[] logoBytes, String number, String dateTime) {
            this.fonts = fonts;
            this.logoBytes = logoBytes;
            this.number = number;
            this.dateTime = dateTime;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                Rectangle page = document.getPageSize();
                PdfContentByte canvas = writer.getDirectContent();
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(LOGO_WIDTH, 60);
                logo.setAbsolutePosition(MARGIN, page.getHeight() - MARGIN - logo.getScaledHeight());
                canvas.addImage(logo);

                float valueX = MARGIN + (page.getWidth() - 2 * MARGIN) / 2;
                float y1 = page.getHeight() - 100;
                drawRow(canvas, page, "Номер заказа", number, valueX, y1);
                drawRow(canvas, page, "Дата и время заказа", dateTime, valueX, y1 - 28);
            } catch (DocumentException | IOException e) {
                throw new UncheckedIOException(new IOException("Не удалось нарисовать шапку страницы КП", e));
            }
        }

        private void drawRow(PdfContentByte canvas, Rectangle page, String label, String value, float valueX, float y) {
            canvas.beginText();
            canvas.setColorFill(MUTED);
            canvas.setFontAndSize(fonts.regular(), 10);
            canvas.showTextAligned(Element.ALIGN_LEFT, label, MARGIN, y, 0);
            canvas.setColorFill(DARK);
            canvas.setFontAndSize(fonts.bold(), 10);
            canvas.showTextAligned(Element.ALIGN_LEFT, value, valueX, y, 0);
            canvas.endText();
            canvas.setColorStroke(LINE);
            canvas.setLineWidth(0.7f);
            canvas.moveTo(MARGIN, y - 9);
            canvas.lineTo(page.getWidth() - MARGIN, y - 9);
            canvas.stroke();
        }
    }
}
