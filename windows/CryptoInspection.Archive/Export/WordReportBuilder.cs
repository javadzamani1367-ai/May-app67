using System.Collections.Generic;
using System.IO;
using CryptoInspection.Archive.Data;
using CryptoInspection.Archive.Util;
using DocumentFormat.OpenXml;
using DocumentFormat.OpenXml.Packaging;
using DocumentFormat.OpenXml.Wordprocessing;
using DrawingML = DocumentFormat.OpenXml.Drawing;
using Pictures = DocumentFormat.OpenXml.Drawing.Pictures;
using WordDrawing = DocumentFormat.OpenXml.Drawing.Wordprocessing;

namespace CryptoInspection.Archive.Export
{
    /// <summary>
    /// The same seven sections as an editable `.docx`, for units that need to
    /// paste the report into their own letters. Bidi is set on every paragraph,
    /// which is what makes Word lay it out right to left.
    /// </summary>
    public class WordReportBuilder
    {
        private const int BodySize = 22;
        private const int TitleSize = 30;
        private const int HeadingSize = 26;
        private const long ImageWidthEmu = 5760720;

        private readonly MediaStore _media;

        public WordReportBuilder(MediaStore media)
        {
            _media = media;
        }

        public string Build(ReportDetail detail, string targetPath)
        {
            using (WordprocessingDocument document =
                WordprocessingDocument.Create(targetPath, WordprocessingDocumentType.Document))
            {
                MainDocumentPart main = document.AddMainDocumentPart();
                main.Document = new Document();
                Body body = main.Document.AppendChild(new Body());

                body.AppendChild(Paragraph(Strings.Get("form_org"), BodySize, false, true));
                body.AppendChild(Paragraph(Strings.Get("form_title"), TitleSize, true, true));
                body.AppendChild(Paragraph(
                    PersianNumbers.ToPersian(detail.Report.DisplayCode), HeadingSize, true, true));

                Section(body, "form_section_1", ReportFields.CaseFields(detail));
                Section(body, "form_section_2", ReportFields.LocationFields(detail));
                Section(body, "form_section_3", ReportFields.OwnerFields(detail));
                Section(body, "form_section_4", ReportFields.TechnicalFields(detail));

                body.AppendChild(Heading("form_section_5"));
                body.AppendChild(Grid(ReportFields.DeviceHeader(), ReportFields.DeviceRows(detail)));
                body.AppendChild(Heading("form_section_6"));
                body.AppendChild(Grid(ReportFields.AttendeeHeader(), ReportFields.AttendeeRows(detail)));

                body.AppendChild(Heading("form_section_7"));
                body.AppendChild(Paragraph(detail.Report.Description ?? string.Empty));
                body.AppendChild(Heading("form_actions_taken"));
                body.AppendChild(Paragraph(detail.Report.ActionsTaken ?? string.Empty));

                if (detail.Attachments.Count > 0)
                {
                    body.AppendChild(Heading("form_attachments"));
                    foreach (Attachment attachment in detail.Attachments)
                    {
                        body.AppendChild(Paragraph("- " + Labels.AttachmentCategory(attachment.Category) +
                            " " + (attachment.Title ?? string.Empty)));
                    }
                }

                body.AppendChild(Paragraph(Strings.Get("form_signature")));
                AppendPhotos(main, body, detail);
                body.AppendChild(FinalSection());
                main.Document.Save();
            }

            return targetPath;
        }

        private static void Section(Body body, string titleKey, List<Field> fields)
        {
            body.AppendChild(Heading(titleKey));
            List<List<string>> rows = new List<List<string>>();
            foreach (Field field in fields)
            {
                rows.Add(new List<string> { field.Label, field.Value });
            }

            body.AppendChild(Grid(null, rows));
        }

        private void AppendPhotos(MainDocumentPart main, Body body, ReportDetail detail)
        {
            List<MediaItem> photos = detail.Photos;
            if (photos.Count == 0)
            {
                return;
            }

            body.AppendChild(Heading("form_media_appendix"));
            int index = 1;
            foreach (MediaItem photo in photos)
            {
                string path = _media.Resolve(photo.FilePath);
                if (string.IsNullOrEmpty(path) || !File.Exists(path))
                {
                    continue;
                }

                ImagePart part = main.AddImagePart(ImagePartType.Jpeg);
                using (FileStream stream = File.OpenRead(path))
                {
                    part.FeedData(stream);
                }

                long height = ImageHeightEmu(path);
                body.AppendChild(ImageParagraph(main.GetIdOfPart(part), index, height));
                body.AppendChild(Paragraph(ReportFields.PhotoCaption(detail, photo), 18));
                index++;
            }
        }

        private static long ImageHeightEmu(string path)
        {
            using (System.Drawing.Image image = System.Drawing.Image.FromFile(path))
            {
                if (image.Width <= 0)
                {
                    return ImageWidthEmu;
                }

                return (long)(ImageWidthEmu * ((double)image.Height / image.Width));
            }
        }

        private static Paragraph Paragraph(string text, int size = BodySize, bool bold = false, bool centered = false)
        {
            ParagraphProperties properties = new ParagraphProperties(
                new BiDi(),
                new Justification
                {
                    Val = centered ? JustificationValues.Center : JustificationValues.Right
                });

            Paragraph paragraph = new Paragraph(properties);
            paragraph.AppendChild(TextRun(text ?? string.Empty, size, bold));
            return paragraph;
        }

        private static Run TextRun(string text, int size, bool bold)
        {
            RunProperties properties = new RunProperties(
                new RightToLeftText(),
                new RunFonts { Ascii = AppFonts.FamilyName, HighAnsi = AppFonts.FamilyName, ComplexScript = AppFonts.FamilyName },
                new FontSize { Val = size.ToString() },
                new FontSizeComplexScript { Val = size.ToString() });
            if (bold)
            {
                properties.AppendChild(new Bold());
                properties.AppendChild(new BoldComplexScript());
            }

            Run run = new Run(properties);
            string[] lines = text.Split('\n');
            for (int i = 0; i < lines.Length; i++)
            {
                if (i > 0)
                {
                    run.AppendChild(new Break());
                }

                run.AppendChild(new Text(lines[i]) { Space = SpaceProcessingModeValues.Preserve });
            }

            return run;
        }

        private static Paragraph Heading(string key)
        {
            return Paragraph(Strings.Get(key), HeadingSize, true);
        }

        /// <summary>A bidi table; a null header means a plain label/value grid.</summary>
        private static Table Grid(List<string> header, List<List<string>> rows)
        {
            Table table = new Table(new TableProperties(
                new BiDiVisual(),
                new TableWidth { Width = "5000", Type = TableWidthUnitValues.Pct },
                new TableBorders(
                    new TopBorder { Val = BorderValues.Single, Size = 6, Color = "B9C2C7" },
                    new LeftBorder { Val = BorderValues.Single, Size = 6, Color = "B9C2C7" },
                    new BottomBorder { Val = BorderValues.Single, Size = 6, Color = "B9C2C7" },
                    new RightBorder { Val = BorderValues.Single, Size = 6, Color = "B9C2C7" },
                    new InsideHorizontalBorder { Val = BorderValues.Single, Size = 6, Color = "B9C2C7" },
                    new InsideVerticalBorder { Val = BorderValues.Single, Size = 6, Color = "B9C2C7" })));

            if (header != null)
            {
                TableRow headerRow = new TableRow();
                foreach (string title in header)
                {
                    headerRow.AppendChild(Cell(title, true));
                }

                table.AppendChild(headerRow);
            }

            foreach (List<string> row in rows)
            {
                TableRow tableRow = new TableRow();
                foreach (string value in row)
                {
                    tableRow.AppendChild(Cell(value, false));
                }

                table.AppendChild(tableRow);
            }

            return table;
        }

        private static TableCell Cell(string text, bool header)
        {
            TableCellProperties properties = new TableCellProperties();
            if (header)
            {
                properties.AppendChild(new Shading { Val = ShadingPatternValues.Clear, Fill = "F2F5F7" });
            }

            TableCell cell = new TableCell(properties);
            cell.AppendChild(Paragraph(text ?? string.Empty, BodySize, header));
            return cell;
        }

        private static Paragraph ImageParagraph(string relationshipId, int index, long heightEmu)
        {
            DrawingML.Graphic graphic = new DrawingML.Graphic(
                new DrawingML.GraphicData(
                    new Pictures.Picture(
                        new Pictures.NonVisualPictureProperties(
                            new Pictures.NonVisualDrawingProperties { Id = (UInt32Value)(uint)index, Name = "Picture" + index },
                            new Pictures.NonVisualPictureDrawingProperties()),
                        new Pictures.BlipFill(
                            new DrawingML.Blip { Embed = relationshipId },
                            new DrawingML.Stretch(new DrawingML.FillRectangle())),
                        new Pictures.ShapeProperties(
                            new DrawingML.Transform2D(
                                new DrawingML.Offset { X = 0L, Y = 0L },
                                new DrawingML.Extents { Cx = ImageWidthEmu, Cy = heightEmu }),
                            new DrawingML.PresetGeometry(new DrawingML.AdjustValueList())
                            {
                                Preset = DrawingML.ShapeTypeValues.Rectangle
                            })))
                {
                    Uri = "http://schemas.openxmlformats.org/drawingml/2006/picture"
                });

            Drawing drawing = new Drawing(
                new WordDrawing.Inline(
                    new WordDrawing.Extent { Cx = ImageWidthEmu, Cy = heightEmu },
                    new WordDrawing.DocProperties { Id = (UInt32Value)(uint)index, Name = "Picture" + index },
                    graphic)
                {
                    DistanceFromTop = 0U,
                    DistanceFromBottom = 0U,
                    DistanceFromLeft = 0U,
                    DistanceFromRight = 0U
                });

            Paragraph paragraph = new Paragraph(new ParagraphProperties(
                new Justification { Val = JustificationValues.Center }));
            paragraph.AppendChild(new Run(drawing));
            return paragraph;
        }

        private static SectionProperties FinalSection()
        {
            return new SectionProperties(
                new PageSize { Width = 11906U, Height = 16838U },
                new PageMargin { Top = 850, Right = (UInt32Value)700U, Bottom = 850, Left = (UInt32Value)700U },
                new BiDi());
        }
    }
}
