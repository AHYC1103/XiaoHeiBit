#!/usr/bin/env python3
import sys, os, json
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), 'core'))
os.chdir(os.path.dirname(os.path.abspath(__file__)))
from core import api

if __name__ == '__main__':
    with open('config.json', 'r') as f:
        cfg = json.load(f)
    api.app.run(host=cfg.get('webui_host', '127.0.0.1'),
                port=cfg.get('webui_port', 8234),
                debug=False, threaded=True)
