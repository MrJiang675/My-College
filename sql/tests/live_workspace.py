"""Integration check for a running, disposable local Argus test instance."""
import json
import secrets
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = "http://127.0.0.1:15173/api"

def call(method, path, body=None, token=None, expected=200):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = Request(BASE + path, data=json.dumps(body).encode() if body is not None else None,
                  method=method, headers=headers)
    try:
        with urlopen(req, timeout=10) as response:
            status, payload = response.status, json.load(response)
    except HTTPError as error:
        status, payload = error.code, json.load(error)
    assert status == expected, (method, path, status, payload.get("message"))
    return payload.get("data")

def account():
    # Current product creates a passwordless student on its first named login.
    return {"loginId":"integration_"+secrets.token_hex(12)}


def login(credentials):
    return call("POST", "/auth/login", credentials)["accessToken"]

def main():
    path = "/college/workspace"
    call("GET", path, expected=401)
    student = account()
    first, second, other = login(student), login(student), login(account())
    assert call("GET", path, token=second)["revision"] == 0
    draft = {"major":"机械工程","pages":{"practice":{"draft":"电阻翻倍"}}}
    assert call("PUT", path, {"revision":0,"state":draft}, first) == 1
    read = call("GET", path, token=second)
    assert read["revision"] == 1 and json.loads(read["payload"]) == draft
    call("PUT", path, {"revision":0,"state":{}}, second, expected=409)
    assert call("PUT", path, {"revision":1,"state":{"grade":"大二"}}, second) == 2
    call("PUT", path, {"revision":1,"state":{}}, first, expected=409)
    assert call("GET", path, token=other) == {"revision":0,"payload":"{}"}
    print("PASS: anonymous access, independent logins, persistence, owner isolation, stale inserts and updates")

if __name__ == "__main__":
    main()
