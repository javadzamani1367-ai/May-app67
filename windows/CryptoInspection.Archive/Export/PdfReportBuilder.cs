using System.Collections.Generic;
using System.IO;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Util;
using QuestPDF.Fluent;
using QuestPDF.Helpers;
using QuestPDF.Infrastructure;

namespace CryptoInspection.Archive.Export
{
    /// <summary>
    /// The visit form as PDF. QuestPDF lays the page out right to left and
    /// Vazirmatn is registered from the app folder, so the output matches what
    /// the phone prints, page for page.
    /// </summary>
    public class PdfReportBuilder
    {
        private const string Grey = "#F2F5F7";
        private const string Border = "#B9C2C7";

        private readonly MediaStore _media;

        public PdfReportBuilder(MediaStore media)
        {
            _media = media;
            QuestPDF.Settings.License = LicenseType.Community;
            AppFonts.Register(stream => QuestPDF.Drawing.FontManager.RegisterFont(stream));
        }

        public string Build(ReportDetail detail, string targetPath)
        {
            Document.Create(container =>
            {
                container.Page(page =>
                {
                    page.Size(PageSizes.A4);
                    page.Margin(14, Unit.Millimetre);
                    page.DefaultTextStyle(style => style
                        .FontFamily(AppFonts.ReportFamily)
                        .FontSize(10)
                        .DirectionFromRightToLeft());

                    // RTL is applied on the containers themselves; every child
                    // inherits it, so no section has to think about direction.
                    page.Header().ContentFromRightToLeft().Element(header => Header(header, detail));
                    page.Content().ContentFromRightToLeft().Element(content => Content(content, detail));
                    page.Footer().AlignCenter().Text(text =>
                    {
                        text.Span(Strings.Get("form_page") + " ");
                        text.CurrentPageNumber();
                    });
                });
            }).GeneratePdf(targetPath);

            return targetPath;
        }

        private static void Header(IContainer container, ReportDetail detail)
        {
            container.PaddingBottom(6).Column(column =>
            {
                column.Item().AlignCenter().Text(Strings.Get("form_org")).FontSize(11);
                column.Item().AlignCenter().Text(Strings.Get("form_title")).FontSize(14).Bold();
                column.Item().AlignCenter().Text(PersianNumbers.ToPersian(detail.Report.DisplayCode))
                    .FontSize(13).Bold();
                column.Item().PaddingTop(4).LineHorizontal(1).LineColor(Colors.Teal.Darken2);
            });
        }

        private void Content(IContainer container, ReportDetail detail)
        {
            container.Column(column =>
            {
                Section(column, "form_section_1", ReportFields.CaseFields(detail));
                Section(column, "form_section_2", ReportFields.LocationFields(detail));
                Section(column, "form_section_3", ReportFields.OwnerFields(detail));
                Section(column, "form_section_4", ReportFields.TechnicalFields(detail));

                Heading(column, "form_section_5");
                column.Item().Element(element =>
                    Grid(element, ReportFields.DeviceHeader(), ReportFields.DeviceRows(detail)));

                Heading(column, "form_section_6");
                column.Item().Element(element =>
                    Grid(element, ReportFields.AttendeeHeader(), ReportFields.AttendeeRows(detail)));

                Heading(column, "form_section_7");
                column.Item().Text(detail.Report.Description ?? string.Empty);
                Heading(column, "form_actions_taken");
                column.Item().Text(detail.Report.ActionsTaken ?? string.Empty);

                if (detail.Attachments.Count > 0)
                {
                    Heading(column, "form_attachments");
                    foreach (Attachment attachment in detail.Attachments)
                    {
                        column.Item().Text("- " + Labels.AttachmentCategory(attachment.Category) +
                            " " + (attachment.Title ?? string.Empty));
                    }
                }

                column.Item().PaddingTop(20).AlignLeft().Text(Strings.Get("form_signature"));

                List<MediaItem> photos = detail.Photos;
                if (photos.Count > 0)
                {
                    column.Item().PageBreak();
                    Heading(column, "form_media_appendix");
                    foreach (MediaItem photo in photos)
                    {
                        string path = _media.Resolve(photo.FilePath);
                        if (string.IsNullOrEmpty(path) || !File.Exists(path))
                        {
                            continue;
                        }

                        column.Item().PaddingBottom(10).Column(photoColumn =>
                        {
                            photoColumn.Item().MaxHeight(105, Unit.Millimetre).Image(path).FitArea();
                            photoColumn.Item().PaddingTop(2)
                                .Text(ReportFields.PhotoCaption(detail, photo)).FontSize(8);
                        });
                    }
                }
            });
        }

        private static void Section(ColumnDescriptor column, string titleKey, List<Field> fields)
        {
            Heading(column, titleKey);
            column.Item().Table(table =>
            {
                table.ColumnsDefinition(columns =>
                {
                    columns.RelativeColumn(1);
                    columns.RelativeColumn(3);
                });

                foreach (Field field in fields)
                {
                    table.Cell().Element(Cell).Background(Grey).Text(field.Label);
                    table.Cell().Element(Cell).Text(field.Value);
                }
            });
        }

        private static void Grid(IContainer container, List<string> header, List<List<string>> rows)
        {
            container.Table(table =>
            {
                table.ColumnsDefinition(columns =>
                {
                    for (int i = 0; i < header.Count; i++)
                    {
                        columns.RelativeColumn(i == 0 ? 1 : 3);
                    }
                });

                foreach (string title in header)
                {
                    table.Cell().Element(Cell).Background(Grey).Text(title).Bold();
                }

                foreach (List<string> row in rows)
                {
                    foreach (string value in row)
                    {
                        table.Cell().Element(Cell).Text(value ?? string.Empty);
                    }
                }
            });
        }

        private static void Heading(ColumnDescriptor column, string key)
        {
            column.Item().PaddingTop(10).PaddingBottom(4)
                .Text(Strings.Get(key)).FontSize(11).Bold();
        }

        private static IContainer Cell(IContainer container)
        {
            return container
                .Border(0.5f)
                .BorderColor(Border)
                .Padding(4);
        }
    }
}
