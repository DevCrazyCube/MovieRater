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
