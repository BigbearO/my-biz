# -*- coding:utf-8 -*-

from flask import Flask, request

app = Flask(__name__)


@app.route("/index", methods=["POST", "GET"])
def index2():
    if request.method == "GET":
        name = request.args.get("name")
        print("GET:>>>>>>>>>>>>>>>>>>>>>")
        ret = {
            "msg": f"hello GET {name}"
        }
        return ret

    if request.method == "POST":
        name = request.json.get("name")
        print("POST:>>>>>>>>>>>>>>>>>>>>>")
        ret = {
            "msg": f"hello POST {name}"
        }
        return ret


if __name__ == '__main__':
    app.run(host="127.0.0.1", port=9099)
