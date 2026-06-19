# MovieRater

A small command-line application that lets **MovieRater** manage its viewing-habit
data. Written in **Java** using **JDBC** on top of a **SQLite** database.

The program reads the supplied dataset (`data/viewing_habits.csv`), stores it in a
SQLite database according to the Entity Relationship Diagram, and offers a menu
to manage and query that data. All calculations (means, totals, counts) are done
in **SQL**, not in Java.

---

## Database design (ERD)

The flat CSV is normalised into the three tables from the assignment:

```
User                    ViewingHabit                 Movie
----------------        ------------------           ----------------
UserID  (PK)  ----<     UserID  (FK)                 MovieID (PK)
Age                     MovieID (FK)      >----      Title
                        MinutesWatched               ReleaseYear
                                                     Director
                                                     Genre
```

- `User` and `Movie` hold each entity once.
- `ViewingHabit` is the link table: one row per (user, movie) pair with the
  minutes that user watched of that movie.

The schema lives in [`src/schema.sql`](src/schema.sql).

---

## Design rationale

This section explains *why* the application is built the way it is — useful for
the oral defense.

### Why normalise the CSV into three tables?
The supplied CSV is **flat (denormalised)**: every row repeats the user's age and
the full movie details next to the minutes watched. Storing it like that causes
**redundancy** (the title "The Godfather" is written dozens of times) and
**update anomalies** (renaming a movie would mean editing many rows, and could
leave the data inconsistent if one row is missed).

Splitting it into `User`, `Movie` and `ViewingHabit` (the ERD) means each fact is
stored **once**:
- a user's age lives in exactly one `User` row,
- a movie's details live in exactly one `Movie` row,
- the link table `ViewingHabit` records *who watched what, and for how long*.

### Why a link (junction) table?
A user can watch many movies, and a movie can be watched by many users — a
**many-to-many** relationship. A relational database cannot express that directly,
so `ViewingHabit` resolves it: **one row per (user, movie) pair**, carrying the
`MinutesWatched` for that pair. Its two foreign keys point back to `User` and
`Movie`.

### Why calculations are done in SQL, not Java
The assignment requires it, and it is also the better design: the database engine
is optimised for aggregation (`AVG`, `SUM`, `COUNT`, `GROUP BY`). Java only sends
the query and prints the single result, instead of pulling every row across the
JDBC connection and looping over it. Less data transferred, less code, fewer bugs.

### Key implementation choices
- **JDBC with `PreparedStatement`** for every query that takes user input
  (UserID, MovieID, age, new title). The values are bound as parameters (`?`),
  which prevents **SQL injection** and handles types/quoting correctly.
- **`INSERT OR IGNORE`** when importing users and movies: because each
  user/movie appears on many CSV rows, this stores each one only once instead of
  failing on the duplicate primary key.
- **One transaction for the import** (`setAutoCommit(false)` + `commit`): loading
  all rows in a single transaction is much faster than committing each insert, and
  if anything fails the whole import is rolled back so the database is never left
  half-filled.
- **`AUTOINCREMENT`-style UserID**: `UserID INTEGER PRIMARY KEY` is an alias for
  SQLite's rowid, so a new user gets the next free ID automatically — we read it
  back with `getGeneratedKeys()`.
- **Foreign keys enabled** (`PRAGMA foreign_keys = ON`): SQLite does not enforce
  them by default, so we switch it on for every connection to keep the data
  consistent.

### Notes on specific queries
- **Users who watched a movie (option 6)** uses `COUNT(DISTINCT UserID)` so a user
  is counted only once, and `MinutesWatched > 0` so someone with a 0-minute record
  is not counted as having "watched" it.
- **Users with more than one movie (option 8)** groups the habits per user and
  keeps only those linked to **2 or more distinct movies**
  (`GROUP BY UserID HAVING COUNT(DISTINCT MovieID) > 1`), then counts them. Using
  `DISTINCT MovieID` means watching the *same* movie twice does not count as two.

---

## Requirements

- **Java 17 or newer** (`java -version` to check — developed on Java 21)
- No build tools needed. The SQLite JDBC driver is included in the `lib/` folder.

---

## How to run

From the project root:

```bash
./compile.sh      # compiles the Java sources into out/
./run.sh          # compiles if needed, then starts the application
```

On **Windows** (without bash), run the same two commands manually:

```bat
javac -cp "lib/sqlite-jdbc.jar" -d out src\*.java
java  -cp "out;lib/*" MovieRaterApp
```

On the **first run** the program automatically builds `data/movierater.db` from
the CSV. A ready-built database is also included in the repository, so it works
out of the box. Choose option **r** in the menu at any time to rebuild the
database from the CSV (this resets all changes).

---

## Menu / functionalities

When the program starts you get this menu:

| Option | Functionality | How it works (SQL) |
|-------|----------------|--------------------|
| 1 | **Add a user** | `INSERT INTO User(Age)` — the UserID is generated automatically and shown. |
| 2 | **Show all viewing-habit data for a user** | `SELECT` from `ViewingHabit` joined with `Movie`, filtered by `UserID`. |
| 3 | **Change the title of a movie** | `UPDATE Movie SET Title = ? WHERE MovieID = ?`. |
| 4 | **Delete a record from ViewingHabit** | `DELETE FROM ViewingHabit WHERE UserID = ? AND MovieID = ?`. |
| 5 | **Mean age of the users** | `SELECT AVG(Age) FROM User`. |
| 6 | **Number of users that watched a specific movie** | `SELECT COUNT(DISTINCT UserID) FROM ViewingHabit WHERE MovieID = ? AND MinutesWatched > 0`. |
| 7 | **Total minutes watched by all users** | `SELECT SUM(MinutesWatched) FROM ViewingHabit`. |
| 8 | **Users that watched more than one movie** | `COUNT` of users `GROUP BY UserID HAVING COUNT(DISTINCT MovieID) > 1`. |
| 9 | **Add an `Email` (TEXT) column to User** | `ALTER TABLE User ADD COLUMN Email TEXT`. |
| r | Rebuild the database from the CSV | drops the tables and re-imports the CSV. |
| 0 | Exit | |

Each option prompts for any input it needs (for example a UserID or MovieID).

### Example session

```
Choose an option: 5
The mean age of all users is 32.26 years.

Choose an option: 6
Enter the MovieID: 1
22 user(s) watched minutes from movie 1.

Choose an option: 7
Total minutes watched by all users: 30786.

Choose an option: 8
99 user(s) have watched more than one movie.
```

---

## Project structure

```
MovieRater/
├── src/
│   ├── schema.sql          # table definitions (the ERD)
│   ├── Database.java       # JDBC connection + CSV import / normalisation
│   └── MovieRaterApp.java  # menu + the 9 functionalities
├── data/
│   ├── viewing_habits.csv  # the supplied dataset
│   └── movierater.db       # SQLite database (auto-built from the CSV)
├── lib/
│   └── sqlite-jdbc.jar     # SQLite JDBC driver (+ slf4j)
├── compile.sh
├── run.sh
└── README.md
```
