using System;
using System.Data;
using System.IO;
using Microsoft.Data.Sqlite;

namespace CryptoInspection.Archive.Data
{
    /// <summary>
    /// The central archive file: SQLite through SQLCipher, same encryption as
    /// the phone. Connections are short lived; SQLite handles that better than
    /// one long lived connection shared across a UI.
    /// </summary>
    public class Database
    {
        private readonly string _connectionString;

        static Database()
        {
            // Selects the SQLCipher build of the native library. Harmless if the
            // bundle already initialised itself.
            SQLitePCL.Batteries_V2.Init();
        }

        public Database(string path, string password)
        {
            Path = path;
            string directory = System.IO.Path.GetDirectoryName(path);
            if (!string.IsNullOrEmpty(directory) && !Directory.Exists(directory))
            {
                Directory.CreateDirectory(directory);
            }

            SqliteConnectionStringBuilder builder = new SqliteConnectionStringBuilder
            {
                DataSource = path,
                Mode = SqliteOpenMode.ReadWriteCreate
            };
            if (!string.IsNullOrEmpty(password))
            {
                builder.Password = password;
            }

            _connectionString = builder.ToString();
        }

        public string Path { get; private set; }

        public SqliteConnection Open()
        {
            SqliteConnection connection = new SqliteConnection(_connectionString);
            connection.Open();
            Execute(connection, "PRAGMA foreign_keys = ON");
            return connection;
        }

        public void EnsureCreated()
        {
            using (SqliteConnection connection = Open())
            using (SqliteTransaction transaction = connection.BeginTransaction())
            {
                foreach (string statement in Schema.Statements)
                {
                    Execute(connection, statement, transaction);
                }

                transaction.Commit();
            }
        }

        public static void Execute(
            SqliteConnection connection,
            string sql,
            SqliteTransaction transaction = null,
            Action<SqliteCommand> bind = null)
        {
            using (SqliteCommand command = connection.CreateCommand())
            {
                command.CommandText = sql;
                if (transaction != null)
                {
                    command.Transaction = transaction;
                }

                if (bind != null)
                {
                    bind(command);
                }

                command.ExecuteNonQuery();
            }
        }

        public static object Scalar(SqliteConnection connection, string sql, Action<SqliteCommand> bind = null)
        {
            using (SqliteCommand command = connection.CreateCommand())
            {
                command.CommandText = sql;
                if (bind != null)
                {
                    bind(command);
                }

                return command.ExecuteScalar();
            }
        }

        /// <summary>Null aware parameter binding; the schema is full of nullables.</summary>
        public static void Bind(SqliteCommand command, string name, object value)
        {
            command.Parameters.AddWithValue(name, value ?? DBNull.Value);
        }

        public static string GetString(IDataRecord record, int index)
        {
            return record.IsDBNull(index) ? null : record.GetString(index);
        }

        public static long GetLong(IDataRecord record, int index)
        {
            return record.IsDBNull(index) ? 0L : record.GetInt64(index);
        }

        public static long? GetNullableLong(IDataRecord record, int index)
        {
            return record.IsDBNull(index) ? (long?)null : record.GetInt64(index);
        }

        public static int GetInt(IDataRecord record, int index)
        {
            return record.IsDBNull(index) ? 0 : (int)record.GetInt64(index);
        }

        public static double? GetNullableDouble(IDataRecord record, int index)
        {
            return record.IsDBNull(index) ? (double?)null : record.GetDouble(index);
        }
    }
}
