import socket
import struct
import sys
from pathlib import Path

run_dir = Path(__file__).resolve().parent.parent / "run"


def read_props():
    props = {}
    for line in (run_dir / "server.properties").read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            props[key] = value
    return props


def send_packet(sock, request_id, packet_type, body):
    payload = struct.pack("<ii", request_id, packet_type) + body.encode() + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(payload)) + payload)


def read_packet(sock):
    size = struct.unpack("<i", sock.recv(4))[0]
    data = b""
    while len(data) < size:
        data += sock.recv(size - len(data))
    request_id, packet_type = struct.unpack("<ii", data[:8])
    return request_id, data[8:-2].decode(errors="replace")


def run(command, host="127.0.0.1", timeout=600):
    props = read_props()
    with socket.create_connection((host, int(props.get("rcon.port", "25575"))), timeout=timeout) as sock:
        send_packet(sock, 1, 3, props["rcon.password"])
        if read_packet(sock)[0] == -1:
            raise RuntimeError("rcon auth failed")
        send_packet(sock, 2, 2, command)
        return read_packet(sock)[1]


if __name__ == "__main__":
    print(run(" ".join(sys.argv[1:])))
