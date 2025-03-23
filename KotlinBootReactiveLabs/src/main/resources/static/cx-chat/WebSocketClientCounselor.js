class WebSocketClient {
    constructor(url) {
        this.url = url;
        this.socket = null;
        this.eventList = document.getElementById("eventList");
    }

    addEvent(message) {
        const li = document.createElement("li");
        li.textContent = message;
        this.eventList.appendChild(li);
        li.scrollIntoView();
    }

    connect() {
        this.socket = new WebSocket(this.url);

        this.socket.onopen = (event) => {
            this.addEvent("WebSocket is open now.");
        };

        this.socket.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                if (data.type === "sessionId") {
                    this.sessionId = data.id;
                    this.addEvent("Your session ID: " + this.sessionId);
                } else {
                    this.addEvent("Received JSON from server: " + event.data);
                }
            } catch (e) {
                this.addEvent("Received text from server: " + event.data);
            }
        };

        this.socket.onclose = (event) => {
            this.addEvent("WebSocket is closed now.");
        };

        this.socket.onerror = (error) => {
            this.addEvent("WebSocket error: " + error);
        };
    }

    disconnect() {
        if (this.socket) {
            this.socket.close();
            this.addEvent("WebSocket is closed.");
        } else {
            this.addEvent("WebSocket is not connected.");
        }
    }

    login(token) {
        const jsonMessage = JSON.stringify({ type: "login", data: token });
        this.socket.send(jsonMessage);
        this.addEvent("Try Login....");
    }

    sendMessage(message) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            const jsonMessage = JSON.stringify({ type: "message", data: message });
            this.socket.send(jsonMessage);
            this.addEvent("sendMessage: " + jsonMessage);
        } else {
            this.addEvent("WebSocket is not open.");
        }
    }

    sendToRoom(roomName, message) {
        const jsonMessage = JSON.stringify({ type: "sendToRoom", roomName: roomName, data: message });
        this.socket.send(jsonMessage);
        this.addEvent("sendToRoom: " + jsonMessage);
    }


}

const client = new WebSocketClient("ws://localhost:8080/ws-counselor");

function connectWebSocket() {
    client.connect();
}

function disconnectWebSocket() {
    client.disconnect();
}

function login() {
    const token = document.getElementById('token').value;
    client.login(token);
}

function sendMessage() {
    client.sendMessage("hello");
}

function clearEvents() {
    this.eventList = document.getElementById("eventList");
    this.eventList.innerHTML = '';
}

function sendToRoom() {
    const roomName = document.getElementById('roomName').value;
    const message = document.getElementById('chatMessage').value;
    client.sendToRoom(roomName, message);
}