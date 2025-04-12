window.dynamicJsLoader = {
    loadScript: function (url) {
        return new Promise(function (resolve, reject) {
            if (document.querySelector(`script[src="${url}"]`)) {
                resolve(); // 이미 로드된 경우
                return;
            }

            const script = document.createElement('script');
            script.src = url;
            script.onload = () => resolve();
            script.onerror = () => reject(`Failed to load script: ${url}`);
            document.head.appendChild(script);
        });
    }
};
