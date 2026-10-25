"""Actual independent subprocess for watchdog tests; only a local FakeDevice is used."""
from pathlib import Path
import os
import subprocess
import sys
import time
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from controller import Controller
from fake_device import FakeDevice

folder = Path(sys.argv[2])
device = FakeDevice(folder / 'device/state.json')
tool = Controller(device, folder)
if sys.argv[1] == 'child':
    tool.watchdog(interval=.05)
else:
    tool.prepare(60, lambda text: None)
    child = subprocess.Popen([sys.executable, __file__, 'child', str(folder)], start_new_session=True,
                             stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    deadline = time.monotonic() + 5
    while not (folder / 'watchdog-ready.json').exists():
        if time.monotonic() > deadline: raise RuntimeError('Child not ready')
        time.sleep(.05)
    tool.handover()
    journal = tool.journal(); journal['deadline'] = time.time() + 1; tool.save(journal)
    (folder / 'parent-finished').write_text(str(child.pid))
    os._exit(0)  # No finally/rollback in the parent. Child must recover independently.
