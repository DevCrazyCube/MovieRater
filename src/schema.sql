-- MovieRater database schema
-- Matches the Entity Relationship Diagram from the assignment.

PRAGMA foreign_keys = ON;

DROP TABLE IF EXISTS ViewingHabit;
DROP TABLE IF EXISTS Movie;
DROP TABLE IF EXISTS User;

CREATE TABLE User (
    UserID  INTEGER PRIMARY KEY,
    Age     INTEGER
);

CREATE TABLE Movie (
    MovieID     INTEGER PRIMARY KEY,
    Title       TEXT,
    ReleaseYear INTEGER,
    Director    TEXT,
    Genre       TEXT
);

CREATE TABLE ViewingHabit (
    UserID         INTEGER,
    MovieID        INTEGER,
    MinutesWatched INTEGER,
    FOREIGN KEY (UserID)  REFERENCES User(UserID),
    FOREIGN KEY (MovieID) REFERENCES Movie(MovieID)
);
