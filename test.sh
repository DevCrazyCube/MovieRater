#!/usr/bin/env bash
# End-to-end test: builds the app in a temporary copy, feeds the menu scripted
# input and checks what it prints. The repository's own database is not touched.
set -u
here="$(cd "$(dirname "$0")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
cp -r "$here/src" "$here/lib" "$tmp/"
mkdir -p "$tmp/data" "$tmp/out"
cp "$here/data/viewing_habits.csv" "$tmp/data/"
cd "$tmp" || exit 2
javac -cp "lib/sqlite-jdbc.jar" -d out src/*.java || { echo "FAIL: does not compile"; exit 1; }

pass=0; fail=0

# run INPUT: start the app, type INPUT, print everything it says.
run() {
    printf "$1" | java -cp "out:lib/*" MovieRaterApp 2>&1
}

# check NAME INPUT EXPECTED: EXPECTED must be somewhere in the output.
check() {
    local out
    out="$(run "$2")"
    if printf '%s' "$out" | grep -qF -- "$3"; then
        pass=$((pass + 1))
    else
        fail=$((fail + 1))
        echo "FAIL: $1"
        echo "  expected: $3"
        printf '%s\n' "$out" | grep -v '^ *[0-9r]\. \|^---\|^Choose\|^$' | tail -4 | sed 's/^/  | /'
    fi
}

# refuse NAME INPUT UNWANTED: UNWANTED must not be in the output.
refuse() {
    local out
    out="$(run "$2")"
    if printf '%s' "$out" | grep -qF -- "$3"; then
        fail=$((fail + 1)); echo "FAIL: $1 (found: $3)"
    else
        pass=$((pass + 1))
    fi
}

check  "first run builds the database"       '0\n'              "Database created at data/movierater.db"
check  "5: mean age"                         '5\n0\n'           "The mean age of all users is 32.26 years."
check  "6: users for movie 1"                '6\n1\n0\n'        "22 user(s) watched minutes from movie 1."
check  "7: total minutes"                    '7\n0\n'           "Total minutes watched by all users: 30786."
check  "8: users with more than one movie"   '8\n0\n'           "99 user(s) have watched more than one movie."
check  "2: unknown user"                     '2\n99999\n0\n'    "No viewing habits found for user 99999."
check  "6: unknown movie counts zero"        '6\n99999\n0\n'    "0 user(s) watched minutes from movie 99999."
check  "a word where a number is asked"      '6\nabc\n1\n0\n'   "Please enter a whole number."
check  "unknown menu option"                 'x\n0\n'           "Unknown option, please try again."
check  "1: negative age refused"             '1\n-4\n0\n'       "An age must be between 0 and 130."
check  "3: empty title refused"              '3\n1\n\n0\n'      "A title cannot be empty."
check  "3: unknown movie"                    '3\n99999\nX\n0\n' "No movie found with MovieID 99999."
check  "4: no such record"                   '4\n99999\n1\n0\n' "No matching record found."
check  "input closed in the menu"            '5\n'              "Goodbye!"
refuse "input closed: no stack trace"        '6\n'              "Exception"

# These change the data; they run last and in this order.
# User 1's first movie is looked up, so the test does not depend on one row of the CSV.
movie="$(awk -F, 'NR > 1 && $1 == 1 { print $3; exit }' data/viewing_habits.csv)"
check  "1: add a user"                       '1\n30\n0\n'       "User added with UserID"
check  "3: rename, then user 1 shows it"     "3\n$movie\nRenamed Film\n2\n1\n0\n" "Renamed Film"
check  "4: delete a record"                  "4\n1\n$movie\n0\n" "Deleted 1 record(s)."
check  "4: it is gone afterwards"            "4\n1\n$movie\n0\n" "No matching record found."
check  "9: add the Email column"             '9\n0\n'           "Added the 'Email' (TEXT) column"
check  "9: second time says it exists"       '9\n0\n'           "already has an 'Email' column"
check  "r: rebuild puts the data back"       'r\n7\n0\n'        "Total minutes watched by all users: 30786."

echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
