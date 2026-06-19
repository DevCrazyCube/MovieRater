import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

/**
 * MovieRater command-line application.
 *
 * Lets MovieRater manage its viewing-habit data. Every calculation that the
 * assignment asks for (mean, totals, counts) is done in SQL, not in Java.
 *
 * Database layout (from the ERD):
 *   User(UserID PK, Age)
 *   Movie(MovieID PK, Title, ReleaseYear, Director, Genre)
 *   ViewingHabit(UserID FK, MovieID FK, MinutesWatched)
 */
public class MovieRaterApp {

    private static final String DB_PATH     = "data/movierater.db";
    private static final String SCHEMA_PATH = "src/schema.sql";
    private static final String CSV_PATH    = "data/viewing_habits.csv";

    private final Database db = new Database(DB_PATH);
    private final Scanner in  = new Scanner(System.in);

    public static void main(String[] args) throws Exception {
        new MovieRaterApp().run();
    }

    private void run() throws Exception {
        ensureDatabase();
        System.out.println("=== Welcome to MovieRater ===");

        boolean running = true;
        while (running) {
            printMenu();
            String choice = in.nextLine().trim();
            System.out.println();
            switch (choice) {
                case "1"  -> addUser();
                case "2"  -> showViewingHabitsForUser();
                case "3"  -> changeMovieTitle();
                case "4"  -> deleteViewingHabit();
                case "5"  -> showMeanAge();
                case "6"  -> showUserCountForMovie();
                case "7"  -> showTotalMinutesWatched();
                case "8"  -> showUsersWithMoreThanOneMovie();
                case "9"  -> addEmailColumn();
                case "0"  -> running = false;
                case "r"  -> rebuildDatabase();
                default   -> System.out.println("Unknown option, please try again.");
            }
            System.out.println();
        }
        System.out.println("Goodbye!");
    }

    /** Builds the database from the CSV the first time the app is run. */
    private void ensureDatabase() throws SQLException, IOException {
        if (!Files.exists(Path.of(DB_PATH))) {
            System.out.println("First run: building database from " + CSV_PATH + " ...");
            db.initialise(SCHEMA_PATH, CSV_PATH);
            System.out.println("Database created at " + DB_PATH);
        }
    }

    private void rebuildDatabase() throws SQLException, IOException {
        System.out.println("Rebuilding database from the CSV (this resets all changes)...");
        db.initialise(SCHEMA_PATH, CSV_PATH);
        System.out.println("Done.");
    }

    private void printMenu() {
        System.out.println("""
                ------------------------------------------------
                 1. Add a user
                 2. Show all viewing-habit data for a user
                 3. Change the title of a movie
                 4. Delete a record from the ViewingHabit table
                 5. Show the mean age of all users
                 6. Show how many users watched a specific movie
                 7. Show the total minutes watched by all users
                 8. Show how many users watched more than one movie
                 9. Add an 'Email' column to the User table
                 r. Rebuild the database from the CSV
                 0. Exit
                ------------------------------------------------""");
        System.out.print("Choose an option: ");
    }

    // ---------------------------------------------------------------------
    // 1. Add a user
    // ---------------------------------------------------------------------
    private void addUser() {
        int age = askInt("Enter the age of the new user: ");
        String sql = "INSERT INTO User(Age) VALUES (?)";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, age);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    System.out.println("User added with UserID " + keys.getInt(1) + " (Age " + age + ").");
                }
            }
        } catch (SQLException e) {
            System.out.println("Could not add user: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 2. Provide all the viewing-habit data for a certain user
    // ---------------------------------------------------------------------
    private void showViewingHabitsForUser() {
        int userId = askInt("Enter the UserID: ");
        String sql = """
                SELECT v.MovieID, m.Title, v.MinutesWatched
                FROM ViewingHabit v
                JOIN Movie m ON m.MovieID = v.MovieID
                WHERE v.UserID = ?
                ORDER BY v.MovieID""";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                System.out.printf("%-8s %-50s %-15s%n", "MovieID", "Title", "MinutesWatched");
                boolean any = false;
                while (rs.next()) {
                    any = true;
                    System.out.printf("%-8d %-50s %-15d%n",
                            rs.getInt("MovieID"),
                            rs.getString("Title"),
                            rs.getInt("MinutesWatched"));
                }
                if (!any) System.out.println("No viewing habits found for user " + userId + ".");
            }
        } catch (SQLException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 3. Change the title of a movie
    // ---------------------------------------------------------------------
    private void changeMovieTitle() {
        int movieId = askInt("Enter the MovieID to rename: ");
        System.out.print("Enter the new title: ");
        String newTitle = in.nextLine().trim();
        String sql = "UPDATE Movie SET Title = ? WHERE MovieID = ?";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newTitle);
            ps.setInt(2, movieId);
            int updated = ps.executeUpdate();
            if (updated > 0) {
                System.out.println("Movie " + movieId + " renamed to \"" + newTitle + "\".");
            } else {
                System.out.println("No movie found with MovieID " + movieId + ".");
            }
        } catch (SQLException e) {
            System.out.println("Update failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 4. Delete a record/row from the ViewingHabit table
    // ---------------------------------------------------------------------
    private void deleteViewingHabit() {
        int userId  = askInt("Enter the UserID of the record to delete: ");
        int movieId = askInt("Enter the MovieID of the record to delete: ");
        String sql = "DELETE FROM ViewingHabit WHERE UserID = ? AND MovieID = ?";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, movieId);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                System.out.println("Deleted " + deleted + " record(s).");
            } else {
                System.out.println("No matching record found.");
            }
        } catch (SQLException e) {
            System.out.println("Delete failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 5. Provide the mean age of the users  (calculated in SQL with AVG)
    // ---------------------------------------------------------------------
    private void showMeanAge() {
        String sql = "SELECT AVG(Age) AS meanAge FROM User";
        try (Connection conn = db.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                System.out.printf(java.util.Locale.US,
                        "The mean age of all users is %.2f years.%n", rs.getDouble("meanAge"));
            }
        } catch (SQLException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 6. Total number of users that have watched minutes from a specific movie
    // ---------------------------------------------------------------------
    private void showUserCountForMovie() {
        int movieId = askInt("Enter the MovieID: ");
        // COUNT(DISTINCT UserID) so a user is only counted once.
        // MinutesWatched > 0 makes sure they actually watched something.
        String sql = """
                SELECT COUNT(DISTINCT UserID) AS userCount
                FROM ViewingHabit
                WHERE MovieID = ? AND MinutesWatched > 0""";
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println(rs.getInt("userCount") + " user(s) watched minutes from movie " + movieId + ".");
                }
            }
        } catch (SQLException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 7. Total number of minutes watched by all users  (SUM in SQL)
    // ---------------------------------------------------------------------
    private void showTotalMinutesWatched() {
        String sql = "SELECT SUM(MinutesWatched) AS totalMinutes FROM ViewingHabit";
        try (Connection conn = db.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                System.out.println("Total minutes watched by all users: " + rs.getLong("totalMinutes") + ".");
            }
        } catch (SQLException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 8. Total number of users that have watched more than one movie
    // ---------------------------------------------------------------------
    private void showUsersWithMoreThanOneMovie() {
        // Group the habits per user, keep only users linked to 2+ distinct
        // movies, then count how many such users there are.
        String sql = """
                SELECT COUNT(*) AS userCount FROM (
                    SELECT UserID
                    FROM ViewingHabit
                    GROUP BY UserID
                    HAVING COUNT(DISTINCT MovieID) > 1
                )""";
        try (Connection conn = db.connect();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                System.out.println(rs.getInt("userCount") + " user(s) have watched more than one movie.");
            }
        } catch (SQLException e) {
            System.out.println("Query failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // 9. Add a column to the User table named "Email" which contains TEXT data
    // ---------------------------------------------------------------------
    private void addEmailColumn() {
        try (Connection conn = db.connect()) {
            if (columnExists(conn, "User", "Email")) {
                System.out.println("The User table already has an 'Email' column.");
                return;
            }
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE User ADD COLUMN Email TEXT");
                System.out.println("Added the 'Email' (TEXT) column to the User table.");
            }
        } catch (SQLException e) {
            System.out.println("Could not add column: " + e.getMessage());
        }
    }

    /** Checks the table definition to see if a column already exists. */
    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------
    // Small helper for reading a whole number from the user.
    // ---------------------------------------------------------------------
    private int askInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = in.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a whole number.");
            }
        }
    }
}
