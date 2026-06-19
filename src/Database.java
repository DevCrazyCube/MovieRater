import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the SQLite connection and the one-time setup of the database:
 * creating the tables (from the ERD) and loading the dataset from the CSV file.
 *
 * The CSV is "flat" (denormalised): every row repeats the user, movie and the
 * minutes watched. Here we split that flat data back into the three tables of
 * the ERD: User, Movie and ViewingHabit.
 */
public class Database {

    private final String dbPath;

    public Database(String dbPath) {
        this.dbPath = dbPath;
    }

    /** Opens a new JDBC connection to the SQLite database file. */
    public Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        // Enforce the foreign keys declared in the schema.
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }

    /**
     * Creates the tables and loads the data from the CSV.
     * Safe to call multiple times: it drops and recreates everything.
     */
    public void initialise(String schemaPath, String csvPath) throws SQLException, IOException {
        runSchema(schemaPath);
        importCsv(csvPath);
    }

    /** Runs every statement in the schema.sql file. */
    private void runSchema(String schemaPath) throws SQLException, IOException {
        String sql = Files.readString(Path.of(schemaPath), StandardCharsets.UTF_8);
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            // Split on ';' so each CREATE/DROP runs as its own statement.
            for (String statement : sql.split(";")) {
                if (!statement.isBlank()) {
                    st.execute(statement);
                }
            }
        }
    }

    /**
     * Reads the flat CSV and fills the three tables.
     * Uses INSERT OR IGNORE for User and Movie so each user/movie is only
     * stored once, even though they appear on many CSV rows.
     */
    private void importCsv(String csvPath) throws SQLException, IOException {
        List<String[]> rows = readCsv(csvPath);

        String insertUser  = "INSERT OR IGNORE INTO User(UserID, Age) VALUES (?, ?)";
        String insertMovie = "INSERT OR IGNORE INTO Movie(MovieID, Title, ReleaseYear, Director, Genre) VALUES (?, ?, ?, ?, ?)";
        String insertHabit = "INSERT INTO ViewingHabit(UserID, MovieID, MinutesWatched) VALUES (?, ?, ?)";

        try (Connection conn = connect()) {
            conn.setAutoCommit(false); // one transaction = fast bulk load
            try (PreparedStatement userPs  = conn.prepareStatement(insertUser);
                 PreparedStatement moviePs = conn.prepareStatement(insertMovie);
                 PreparedStatement habitPs = conn.prepareStatement(insertHabit)) {

                for (String[] r : rows) {
                    // CSV columns: UserID,Age,MovieID,Title,ReleaseYear,Director,Genre,MinutesWatched
                    int userId      = Integer.parseInt(r[0].trim());
                    int age         = Integer.parseInt(r[1].trim());
                    int movieId     = Integer.parseInt(r[2].trim());
                    String title    = r[3].trim();
                    int releaseYear = Integer.parseInt(r[4].trim());
                    String director = r[5].trim();
                    String genre    = r[6].trim();
                    int minutes     = Integer.parseInt(r[7].trim());

                    userPs.setInt(1, userId);
                    userPs.setInt(2, age);
                    userPs.executeUpdate();

                    moviePs.setInt(1, movieId);
                    moviePs.setString(2, title);
                    moviePs.setInt(3, releaseYear);
                    moviePs.setString(4, director);
                    moviePs.setString(5, genre);
                    moviePs.executeUpdate();

                    habitPs.setInt(1, userId);
                    habitPs.setInt(2, movieId);
                    habitPs.setInt(3, minutes);
                    habitPs.executeUpdate();
                }
                conn.commit();
            } catch (RuntimeException | SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Minimal CSV reader. Handles commas inside double-quoted fields so a title
     * like "Movie, The" would not be split incorrectly. Skips the header row.
     */
    private List<String[]> readCsv(String csvPath) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(csvPath), StandardCharsets.UTF_8)) {
            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                if (header) { header = false; continue; }
                rows.add(parseCsvLine(line));
            }
        }
        return rows;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
