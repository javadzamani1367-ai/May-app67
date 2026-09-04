using System.Collections.Generic;

namespace CryptoInspection.Archive.Data
{
    /// <summary>
    /// Plain rows of the shared schema. Property names mirror the column names
    /// in `windows/SCHEMA.md`; nothing here may drift from the phone.
    /// </summary>
    public class Report
    {
        public string Id { get; set; }
        public string TrackingCode { get; set; }
        public string TempCode { get; set; }
        public int ReportType { get; set; }
        public int Status { get; set; }
        public string ExpertCode { get; set; }
        public long ReportDate { get; set; }
        public long? VisitDate { get; set; }
        public long CreatedAt { get; set; }
        public long UpdatedAt { get; set; }
        public long? SyncedAt { get; set; }

        public string County { get; set; }
        public string District { get; set; }
        public string Address { get; set; }
        public string PostalCode { get; set; }
        public double? Latitude { get; set; }
        public double? Longitude { get; set; }
        public double? GpsAccuracy { get; set; }
        public string FileNumber { get; set; }
        public string BillNumber { get; set; }
        public string SubscriptionNumber { get; set; }
        public string UsageType { get; set; }

        public string OwnerName { get; set; }
        public string OwnerNationalId { get; set; }
        public string OwnerPhone { get; set; }
        public string OwnerRelation { get; set; }

        public double? MeterAmperage { get; set; }
        public double? MeasuredAmperage { get; set; }
        public string ConnectionType { get; set; }
        public string SealStatus { get; set; }

        public string Description { get; set; }
        public string ActionsTaken { get; set; }

        /// <summary>What the operator reads: the final code, else the temporary one.</summary>
        public string DisplayCode
        {
            get { return string.IsNullOrEmpty(TrackingCode) ? TempCode : TrackingCode; }
        }
    }

    public class Device
    {
        public string Id { get; set; }
        public string ReportId { get; set; }
        public int RowNumber { get; set; }
        public string Model { get; set; }
        public string SerialNumber { get; set; }
        public double? PowerWatt { get; set; }
        public int EntryMethod { get; set; }
        public string Note { get; set; }
    }

    public class Attendee
    {
        public string Id { get; set; }
        public string ReportId { get; set; }
        public int Organization { get; set; }
        public string FullName { get; set; }
        public string Position { get; set; }
        public string OrgName { get; set; }
    }

    public class MediaItem
    {
        public string Id { get; set; }
        public string ReportId { get; set; }
        public int Type { get; set; }
        public string FilePath { get; set; }
        public string Caption { get; set; }
        public long CapturedAt { get; set; }
        public double? Latitude { get; set; }
        public double? Longitude { get; set; }
        public long SizeBytes { get; set; }

        public bool IsPhoto
        {
            get { return Type == 0; }
        }
    }

    public class Attachment
    {
        public string Id { get; set; }
        public string ReportId { get; set; }
        public int Category { get; set; }
        public string Title { get; set; }
        public string FilePath { get; set; }
        public string MimeType { get; set; }
        public long AddedAt { get; set; }
        public string Note { get; set; }
    }

    public class Dispatch
    {
        public string Id { get; set; }
        public string ReportId { get; set; }
        public int Unit { get; set; }
        public string IncludedItems { get; set; }
        public string Note { get; set; }
        public int OutputFormat { get; set; }
        public long DispatchedAt { get; set; }
    }

    /// <summary>A whole case with its children — what the exporters consume.</summary>
    public class ReportDetail
    {
        public ReportDetail()
        {
            Devices = new List<Device>();
            Attendees = new List<Attendee>();
            Media = new List<MediaItem>();
            Attachments = new List<Attachment>();
            Dispatches = new List<Dispatch>();
        }

        public Report Report { get; set; }
        public List<Device> Devices { get; set; }
        public List<Attendee> Attendees { get; set; }
        public List<MediaItem> Media { get; set; }
        public List<Attachment> Attachments { get; set; }
        public List<Dispatch> Dispatches { get; set; }

        public double TotalPower
        {
            get
            {
                double total = 0;
                foreach (Device device in Devices)
                {
                    total += device.PowerWatt ?? 0;
                }

                return total;
            }
        }

        public int DeviceCount
        {
            get { return Devices.Count; }
        }

        public List<MediaItem> Photos
        {
            get
            {
                List<MediaItem> photos = new List<MediaItem>();
                foreach (MediaItem item in Media)
                {
                    if (item.IsPhoto)
                    {
                        photos.Add(item);
                    }
                }

                return photos;
            }
        }
    }
}
