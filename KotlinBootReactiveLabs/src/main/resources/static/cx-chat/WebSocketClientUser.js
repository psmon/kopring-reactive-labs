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

    requestCounseling(channel) {
        const jsonMessage = JSON.stringify({ type: "requestCounseling", channel: channel });
        this.socket.send(jsonMessage);
        this.addEvent("Counseling requested for channel: " + channel);
    }

    sendChat(chatMessage) {
        const jsonMessage = JSON.stringify({ type: "sendchat", data: chatMessage });
        this.socket.send(jsonMessage);
        this.addEvent("Chat sent: " + chatMessage);
    }

    sendAction(action) {
        const jsonMessage = JSON.stringify({ type: "action", data: action });
        this.socket.send(jsonMessage);
    }

    sendMessage(message) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            const jsonMessage = JSON.stringify({ type: "message", data: message });
            this.socket.send(jsonMessage);
            this.addEvent("Sent: " + jsonMessage);
        } else {
            this.addEvent("WebSocket is not open.");
        }
    }

    subscribeTopic(topic) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            const jsonMessage = JSON.stringify({ type: "subscribe", topic: topic });
            this.socket.send(jsonMessage);
            this.addEvent("Subscribed to topic: " + topic);
        } else {
            this.addEvent("WebSocket is not open.");
        }
    }

    unsubscribeTopic(topic) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            const jsonMessage = JSON.stringify({ type: "unsubscribe", topic: topic });
            this.socket.send(jsonMessage);
            this.addEvent("Unsubscribed from topic: " + topic);
        } else {
            this.addEvent("WebSocket is not open.");
        }
    }
}

const client = new WebSocketClient("ws://localhost:8080/ws-user");

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

function requestCounseling() {
    const channel = document.getElementById('channel').value;
    client.requestCounseling(channel);
}

function sendChat() {
    const chatMessage = document.getElementById('chatMessage').value;
    client.sendChat(chatMessage);
}

function sendMessage() {
    client.sendMessage("hello");
}

function sendAction() {
    const action = prompt("Enter action:");
    if (action) {
        client.sendAction(action);
    }
}

function subscribeTopic() {
    const topic = prompt("Enter topic to subscribe:");
    if (topic) {
        client.subscribeTopic(topic);
    }
}

function unsubscribeTopic() {
    const topic = prompt("Enter topic to unsubscribe:");
    if (topic) {
        client.unsubscribeTopic(topic);
    }
}

function clearEvents() {
    this.eventList = document.getElementById("eventList");
    this.eventList.innerHTML = '';
}