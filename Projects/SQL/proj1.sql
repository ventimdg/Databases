-- Before running drop any existing views
DROP VIEW IF EXISTS q0;
DROP VIEW IF EXISTS q1i;
DROP VIEW IF EXISTS q1ii;
DROP VIEW IF EXISTS q1iii;
DROP VIEW IF EXISTS q1iv;
DROP VIEW IF EXISTS q2i;
DROP VIEW IF EXISTS q2ii;
DROP VIEW IF EXISTS q2iii;
DROP VIEW IF EXISTS q3i;
DROP VIEW IF EXISTS q3ii;
DROP VIEW IF EXISTS q3iii;
DROP VIEW IF EXISTS q4i;
DROP VIEW IF EXISTS q4iihelper1;
DROP VIEW IF EXISTS stepsize;
DROP VIEW IF EXISTS bounds;
DROP VIEW IF EXISTS q4iih;
DROP VIEW IF EXISTS q4iih2;
DROP VIEW IF EXISTS q4ii;
DROP VIEW IF EXISTS q4iii;
DROP VIEW IF EXISTS q4iv;
DROP VIEW IF EXISTS q4v;

-- Question 0
CREATE VIEW q0(era)
AS
  SELECT MAX(era)
  FROM pitching
  ;

-- Question 1i
CREATE VIEW q1i(namefirst, namelast, birthyear)
AS
  SELECT namefirst, namelast, birthyear
  FROM people
  WHERE people.weight > 300
;

-- Question 1ii
CREATE VIEW q1ii(namefirst, namelast, birthyear)
AS
  SELECT namefirst, namelast, birthyear
  FROM people
  WHERE people.namefirst LIKE '% %'
  ORDER BY namefirst ASC, namelast ASC
;

-- Question 1iii
CREATE VIEW q1iii(birthyear, avgheight, count)
AS
  SELECT birthyear, AVG(height), count(*)
  FROM people
  GROUP BY birthyear
  ORDER BY birthyear ASC
;

-- Question 1iv
CREATE VIEW q1iv(birthyear, avgheight, count)
AS
    SELECT birthyear, AVG(height), count(*)
    FROM people
    GROUP BY birthyear
    HAVING AVG(height) > 70
    ORDER BY birthyear ASC
;

-- Question 2i
CREATE VIEW q2i(namefirst, namelast, playerid, yearid)
AS
  SELECT namefirst, namelast, p.playerid, yearid
  FROM HallofFame AS h LEFT OUTER JOIN people AS p ON p.playerID = h.playerID
  WHERE inducted LIKE 'Y'
  ORDER BY yearid DESC, p.playerid ASC

;

-- Question 2ii
CREATE VIEW q2ii(namefirst, namelast, playerid, schoolid, yearid)
AS
   SELECT namefirst, namelast, p.playerid, schoolid, yearid
   FROM HallofFame NATURAL JOIN people as p LEFT OUTER JOIN collegeplaying as c on p.playerid = c.playerid
   WHERE inducted LIKE 'Y' AND EXISTS
        (SELECT *
        FROM collegeplaying as x NATURAL JOIN schools
        WHERE schoolstate LIKE 'CA' AND schools.schoolid = c.schoolid AND p.playerid = x.playerid
        )
   ORDER BY yearid DESC, p.playerid ASC
;

-- Question 2iii
CREATE VIEW q2iii(playerid, namefirst, namelast, schoolid)
AS
  SELECT p.playerid, namefirst, namelast, schoolid
  FROM HallofFame NATURAL JOIN people as p LEFT OUTER JOIN collegeplaying as c on p.playerid = c.playerid
  WHERE inducted LIKE 'Y'
  ORDER BY p.playerid DESC, schoolid ASC
;

-- Question 3i
CREATE VIEW q3i(playerid, namefirst, namelast, yearid, slg)
AS
  SELECT b.playerid, namefirst, namelast, yearid, CAST(b.H - b.H2b - b.H3B - b.HR + b.H2b * 2 + b.H3B * 3 + b.HR * 4 AS FLOAT) / CAST(b.AB AS FLOAT) AS slg
  FROM batting AS b LEFT OUTER JOIN people on b.playerID = people.playerid
  WHERE b.AB > 50
  ORDER BY slg DESC, yearid ASC, b.playerID ASC
  LIMIT 10
;

-- Question 3ii
CREATE VIEW q3ii(playerid, namefirst, namelast, lslg)
AS
  SELECT b.playerid, namefirst, namelast, CAST(SUM(b.H) - SUM(b.H2b) - SUM(b.H3B) - SUM(b.HR) +
  SUM(b.H2b) * 2 + SUM(b.H3B) * 3 + SUM(b.HR) * 4 AS FLOAT) / CAST(SUM(b.AB) AS FLOAT) AS lslg
  FROM batting AS b NATURAL JOIN people
  GROUP BY b.playerid, namefirst, namelast
  HAVING SUM(b.AB) > 50
  ORDER BY lslg DESC, yearid ASC, b.playerID ASC
  LIMIT 10
  ;

-- Question 3iii
CREATE VIEW q3iii(namefirst, namelast, lslg)
AS
  SELECT namefirst, namelast, CAST(SUM(b.H) - SUM(b.H2b) - SUM(b.H3B) - SUM(b.HR) +
  SUM(b.H2b) * 2 + SUM(b.H3B) * 3 + SUM(b.HR) * 4 AS FLOAT) / CAST(SUM(b.AB) AS FLOAT) AS lslg
  FROM batting AS b LEFT OUTER JOIN people on b.playerID = people.playerid
  GROUP BY b.playerid, namefirst, namelast
  HAVING SUM(b.AB) > 50 AND CAST(SUM(b.H) - SUM(b.H2b) - SUM(b.H3B) - SUM(b.HR) +
                        SUM(b.H2b) * 2 + SUM(b.H3B) * 3 + SUM(b.HR) * 4 AS FLOAT) / CAST(SUM(b.AB) AS FLOAT) >
    (SELECT CAST(SUM(a.H) - SUM(a.H2b) - SUM(a.H3B) - SUM(a.HR) + SUM(a.H2b) * 2 + SUM(a.H3B) * 3 + SUM(a.HR) * 4 AS FLOAT) / CAST(SUM(a.AB) AS FLOAT)
       FROM batting as a
       WHERE a.playerid LIKE 'mayswi01')
  ORDER BY lslg DESC, b.playerID ASC
;

-- Question 4i
CREATE VIEW q4i(yearid, min, max, avg)
AS
  SELECT yearid, MIN(salary), MAX(salary), AVG(salary)
  FROM salaries
  GROUP BY yearid
  ORDER BY yearid ASC
;

CREATE VIEW q4iihelper1
AS
    SELECT *
    FROM salaries
    WHERE yearid = 2016
;

CREATE VIEW stepsize(step, minimum)
AS
    SELECT (MAX(q.salary) - MIN(q.salary))/10.0, MIN(q.salary)
    FROM q4iihelper1 as q
;

CREATE VIEW bounds(binid, low, high)
AS
    SELECT binid, step * binid + minimum, step * (binid + 1) + minimum
    FROM binids INNER JOIN stepsize
    ON step > 0 or step <= 0
    GROUP BY binid
;

CREATE VIEW q4iih(binid, low, high)
AS
    SELECT binid, binid * step + minimum, (binid +1) * step + minimum
    FROM q4iihelper1 AS q INNER JOIN stepsize
    ON step > 0 or step <= 0
    LEFT OUTER JOIN binids
    ON (q.salary >= step * binid + minimum AND q.salary < step * (binid + 1) + minimum) OR (q.salary >= step * binid + minimum AND q.salary <= step * (binid + 1) + minimum AND binid = 9)
;

CREATE VIEW q4iih2(binid, low, high)
AS
    SELECT *
    FROM q4iih

    UNION ALL

    SELECT *
    FROM bounds
;
-- Question 4ii
CREATE VIEW q4ii(binid, low, high, count)
AS
    SELECT binid, low, high, COUNT(*) - 1
    FROM q4iih2
    GROUP BY binid, low, high
    ORDER BY binid
;

-- Question 4iii
CREATE VIEW q4iii(yearid, mindiff, maxdiff, avgdiff)
AS
  SELECT l.yearid, l.min - r.min, l.max - r.max, l.avg - r.avg
  FROM q4i as l INNER JOIN q4i as r
  ON l.yearid = r.yearid + 1 AND l.min IS NOT NULL AND l.max IS NOT NULL AND l.avg IS NOT NULL
  GROUP BY l.yearid, r.yearid
  ORDER BY l.yearid
;

-- Question 4iv
CREATE VIEW q4iv(playerid, namefirst, namelast, salary, yearid)
AS
  SELECT p.playerid, namefirst, namelast, salary, s.yearid
  FROM people AS p NATURAL JOIN salaries AS s
  LEFT OUTER JOIN q4i AS q on q.yearid = s.yearid
  WHERE (s.yearid = 2001 OR s.yearid = 2000) AND s.salary = q.max
;
-- Question 4v
CREATE VIEW q4v(team, diffAvg) AS
  SELECT a.teamid, MAX(salary) - MIN(Salary)
  FROM salaries as s INNER JOIN allstarfull as a
  ON s.yearid = a.yearid AND s.playerid = a.playerid AND s.teamid = a.teamid
  WHERE s.yearid = 2016
  GROUP BY a.teamid

;

