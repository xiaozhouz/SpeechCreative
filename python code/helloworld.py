from flask import Flask
from flask import request
import requests
import json

app = Flask(__name__)


@app.route('/')
def hello_world():
    return "Hello world!"

def emotion(resulttext):
    endpoint = "https://mscemo.cognitiveservices.azure.cn/"
    sentiment_url = endpoint + "/text/analytics/v2.1/sentiment"

    documents = {"documents": [
        {"id": "1", "language": "zh-hans",
         "text": resulttext},
    ]}
    # documents["documents"].append({"id":"2","language":"zh-hans","text":"我爱你"})
    headers = {"Ocp-Apim-Subscription-Key": "4cd37fd58c524dfea460103e66a62674"}
    response = requests.post(sentiment_url, headers=headers, json=documents)
    sentiments = response.json()
    return ((sentiments['documents'])[0])['score']

@app.route('/text_process', methods=['POST'])
def text_process():
    text_ = request.form['resultText']
    value = emotion(text_)
    return str(value)


@app.route('/test')
def text():
    return 'ok!!'


if __name__ == '__main__':
    app.run(debug="True",host='0.0.0.0')
