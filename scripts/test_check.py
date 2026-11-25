import datetime as dt
import unittest
from check import validate


class ValidationTest(unittest.TestCase):
    def fixture(self, year):
        day = dt.date(year, 1, 1)
        rows = []
        while day.year == year:
            kind = 3 if day.month == day.day == 1 else (2 if day.weekday() >= 5 else 1)
            rows.append({'fecha': day.isoformat(), 'tipo': {'id': kind}})
            day += dt.timedelta(days=1)
        return rows

    def test_matrix(self):
        for year in (2027, 2028):
            validate(self.fixture(year), year)

    def test_duplicate(self):
        rows = self.fixture(2028)
        rows[-1] = rows[0]
        with self.assertRaises(AssertionError):
            validate(rows, 2028)

    def test_classification(self):
        rows = self.fixture(2027)
        rows[1]['tipo']['id'] = 1
        with self.assertRaises(AssertionError):
            validate(rows, 2027)
