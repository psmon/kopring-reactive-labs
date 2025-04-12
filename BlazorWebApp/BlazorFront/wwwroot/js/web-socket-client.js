window.webSocketClient = {
    socket: null,

    connect: function (url, dotNetHelper) {
        this.socket = new WebSocket(url);

        this.socket.onopen = function (event) {
            console.log("WebSocket is open now.");
            dotNetHelper.invokeMethodAsync('ReceiveMessage', "WebSocket is open now.");
        };

        this.socket.onmessage = function (event) {
            console.log("Received from server: " + event.data);
            dotNetHelper.invokeMethodAsync('ReceiveMessage', event.data);
        };

        this.socket.onclose = function (event) {
            console.log("WebSocket is closed now.");
            dotNetHelper.invokeMethodAsync('ReceiveMessage', "WebSocket is closed now.");
        };

        this.socket.onerror = function (error) {
            console.error("WebSocket error: " + error);
            dotNetHelper.invokeMethodAsync('ReceiveMessage', "WebSocket error: " + error);
        };
    },

    disconnect: function () {
        if (this.socket) {
            this.socket.close();
            console.log("WebSocket is closed.");
        } else {
            console.log("WebSocket is not connected.");
        }
    },

    sendMessage: function (message) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            this.socket.send(message);
            console.log("Sent: " + message);
        } else {
            console.log("WebSocket is not open.");
        }
    },

    subscribeTopic: function (topic) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            this.socket.send("subscribe:" + topic);
            console.log("Subscribed to topic: " + topic);
        } else {
            console.log("WebSocket is not open.");
        }
    },

    unsubscribeTopic: function (topic) {
        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
            this.socket.send("unsubscribe:" + topic);
            console.log("Unsubscribed from topic: " + topic);
        } else {
            console.log("WebSocket is not open.");
        }
    }
};