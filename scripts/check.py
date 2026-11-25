"""Opt-in integration check; run only against this delivery after startup approval."""
import datetime as dt
import json
import urllib.request


def validate(rows, year):
    start = dt.date(year, 1, 1)
    expected = {(start + dt.timedelta(days=i)).isoformat()
                for i in range((dt.date(year + 1, 1, 1) - start).days)}
    assert len(rows) == len(expected)
    assert {r['fecha'] for r in rows} == expected
    for row in rows:
        day = dt.date.fromisoformat(row['fecha'])
        kind = row['tipo']['id']
        assert kind in (1, 2, 3)
        if kind != 3:
            assert kind == (2 if day.weekday() >= 5 else 1)
    assert next(r for r in rows if r['fecha'] == f'{year}-01-01')['tipo']['id'] == 3


def request(path, method='GET'):
    with urllib.request.urlopen(urllib.request.Request(
            'http://127.0.0.1:8081/api/calendario/' + path,
            method=method), timeout=60) as response:
        return json.load(response)


def main():
    for year in (2027, 2028):
        assert request(f'generar/{year}', 'POST') is True
        validate(request(f'listar/{year}'), year)
    untouched = request('listar/2028')
    assert request('generar/2027', 'POST') is True
    validate(request('listar/2027'), 2027)
    assert request('listar/2028') == untouched
    print('PASS: 2027/2028, uniqueness, classification, isolated regeneration')


if __name__ == '__main__':
    main()
