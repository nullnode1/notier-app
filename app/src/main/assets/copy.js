(() => {
    const buttons = document.querySelectorAll('button[data-code]');
    let port;

    window.addEventListener('message', event => {
        if (port || event.data !== 'notier-code-copy' || event.ports.length !== 1) return;
        if (event.source && event.source !== window) return;
        if (event.origin && event.origin !== 'null' && event.origin !== location.origin) return;
        port = event.ports[0];
        port.onmessage = reply => {
            const index = Number(reply.data);
            const button = buttons[index];
            if (!button) return;
            button.classList.add('copied');
            button.setAttribute('aria-label', button.dataset.copied);
            clearTimeout(button.copyReset);
            button.copyReset = setTimeout(() => {
                button.classList.remove('copied');
                button.setAttribute('aria-label', button.title);
            }, 1200);
        };
        for (const button of buttons) {
            button.disabled = false;
            button.addEventListener('click', () => port.postMessage(button.dataset.code));
        }
    });
})();
