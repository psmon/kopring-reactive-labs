// wwwroot/js/sseClient.js
window.startSSE = (url, dotNetHelper) => {
    const eventSource = new EventSource(url);

    eventSource.onmessage = function (event) {
        dotNetHelper.invokeMethodAsync('ReceiveMessage', event.data);
    };

    eventSource.onerror = function (error) {
        console.error("EventSource failed: ", error);
    };
};
