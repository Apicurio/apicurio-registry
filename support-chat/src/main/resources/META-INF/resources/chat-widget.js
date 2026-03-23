(function () {
    'use strict';

    var API_BASE = document.currentScript.src.replace(/\/chat-widget\.js.*$/, '');
    var sessionId = null;
    var isOpen = false;

    // Inject styles
    var style = document.createElement('style');
    style.textContent = [
        '#apicurio-chat-bubble{position:fixed;bottom:24px;right:24px;width:56px;height:56px;border-radius:50%;',
        'background:#3b82f6;color:#fff;border:none;cursor:pointer;box-shadow:0 4px 16px rgba(59,130,246,.4);',
        'display:flex;align-items:center;justify-content:center;z-index:99999;transition:transform .2s,box-shadow .2s}',
        '#apicurio-chat-bubble:hover{transform:scale(1.08);box-shadow:0 6px 24px rgba(59,130,246,.5)}',
        '#apicurio-chat-bubble svg{width:28px;height:28px;fill:currentColor}',
        '#apicurio-chat-panel{position:fixed;bottom:92px;right:24px;width:400px;height:560px;',
        'background:#1e1e2e;border-radius:16px;box-shadow:0 8px 40px rgba(0,0,0,.4);z-index:99999;',
        'display:none;flex-direction:column;overflow:hidden;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;',
        'border:1px solid #333}',
        '#apicurio-chat-panel.open{display:flex}',
        '#apicurio-chat-header{padding:16px;background:#252536;border-bottom:1px solid #333;',
        'display:flex;align-items:center;justify-content:space-between}',
        '#apicurio-chat-header h3{margin:0;font-size:15px;color:#e4e4e7;font-weight:600}',
        '#apicurio-chat-header span{font-size:12px;color:#71717a;display:block;margin-top:2px}',
        '#apicurio-chat-close{background:none;border:none;color:#71717a;cursor:pointer;font-size:20px;padding:4px 8px;border-radius:6px}',
        '#apicurio-chat-close:hover{background:#333;color:#e4e4e7}',
        '#apicurio-chat-messages{flex:1;overflow-y:auto;padding:16px;display:flex;flex-direction:column;gap:12px}',
        '.achat-msg{max-width:85%;padding:10px 14px;border-radius:12px;font-size:14px;line-height:1.5;word-wrap:break-word}',
        '.achat-msg.user{align-self:flex-end;background:#3b82f6;color:#fff;border-bottom-right-radius:4px}',
        '.achat-msg.assistant{align-self:flex-start;background:#2a2a3c;color:#e4e4e7;border-bottom-left-radius:4px}',
        '.achat-msg.system{align-self:center;color:#71717a;font-size:13px;font-style:italic;background:none;padding:4px}',
        '.achat-msg.assistant pre{background:#18181b;padding:8px 10px;border-radius:6px;overflow-x:auto;margin:6px 0;font-size:12px}',
        '.achat-msg.assistant code{font-family:"Fira Code",Consolas,monospace;font-size:12px}',
        '.achat-msg.assistant p{margin:6px 0}.achat-msg.assistant ul,.achat-msg.assistant ol{margin:6px 0 6px 16px}',
        '.achat-msg.assistant li{margin:3px 0}',
        '.achat-msg.assistant strong{color:#93c5fd}',
        '.achat-loading span{display:inline-block;width:7px;height:7px;background:#60a5fa;border-radius:50%;',
        'animation:achat-bounce 1.4s infinite ease-in-out}',
        '.achat-loading span:nth-child(1){animation-delay:-.32s}.achat-loading span:nth-child(2){animation-delay:-.16s}',
        '@keyframes achat-bounce{0%,80%,100%{transform:scale(0)}40%{transform:scale(1)}}',
        '#apicurio-chat-input{padding:12px;border-top:1px solid #333;display:flex;gap:8px}',
        '#apicurio-chat-input input{flex:1;padding:10px 12px;border:1px solid #333;border-radius:8px;',
        'background:#18181b;color:#e4e4e7;font-size:14px;outline:none}',
        '#apicurio-chat-input input:focus{border-color:#3b82f6}',
        '#apicurio-chat-input input::placeholder{color:#555}',
        '#apicurio-chat-input button{padding:10px 16px;background:#3b82f6;color:#fff;border:none;border-radius:8px;',
        'font-size:14px;cursor:pointer;transition:background .2s}',
        '#apicurio-chat-input button:hover{background:#2563eb}',
        '#apicurio-chat-input button:disabled{background:#333;cursor:not-allowed}',
        '@media(max-width:480px){#apicurio-chat-panel{width:calc(100vw - 16px);height:calc(100vh - 120px);',
        'right:8px;bottom:80px;border-radius:12px}}'
    ].join('\n');
    document.head.appendChild(style);

    // Chat bubble
    var bubble = document.createElement('button');
    bubble.id = 'apicurio-chat-bubble';
    bubble.title = 'Apicurio Registry Support';
    bubble.innerHTML = '<svg viewBox="0 0 24 24"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/></svg>';
    bubble.onclick = togglePanel;

    // Chat panel
    var panel = document.createElement('div');
    panel.id = 'apicurio-chat-panel';
    panel.innerHTML = [
        '<div id="apicurio-chat-header">',
        '  <div><h3>Apicurio Registry Support</h3><span>AI-powered assistant</span></div>',
        '  <button id="apicurio-chat-close" onclick="document.getElementById(\'apicurio-chat-panel\').classList.remove(\'open\')">&times;</button>',
        '</div>',
        '<div id="apicurio-chat-messages">',
        '  <div class="achat-msg system">Ask me anything about Apicurio Registry!</div>',
        '</div>',
        '<div id="apicurio-chat-input">',
        '  <input type="text" placeholder="Ask about Apicurio Registry..." id="apicurio-chat-text">',
        '  <button id="apicurio-chat-send">Send</button>',
        '</div>'
    ].join('');

    document.body.appendChild(bubble);
    document.body.appendChild(panel);

    // Bind events
    document.getElementById('apicurio-chat-send').onclick = sendMessage;
    document.getElementById('apicurio-chat-text').onkeypress = function (e) {
        if (e.key === 'Enter') sendMessage();
    };
    document.getElementById('apicurio-chat-close').onclick = function () {
        panel.classList.remove('open');
        isOpen = false;
    };

    function togglePanel() {
        isOpen = !isOpen;
        if (isOpen) {
            panel.classList.add('open');
            if (!sessionId) createSession();
            document.getElementById('apicurio-chat-text').focus();
        } else {
            panel.classList.remove('open');
        }
    }

    function createSession() {
        fetch(API_BASE + '/support/session', { method: 'POST' })
            .then(function (r) { return r.json(); })
            .then(function (d) { sessionId = d.sessionId; })
            .catch(function () {});
    }

    function sendMessage() {
        var input = document.getElementById('apicurio-chat-text');
        var msg = input.value.trim();
        if (!msg) return;

        addMsg(msg, 'user');
        input.value = '';
        document.getElementById('apicurio-chat-send').disabled = true;

        var loading = document.createElement('div');
        loading.className = 'achat-msg assistant achat-loading';
        loading.innerHTML = '<span></span><span></span><span></span>';
        var msgs = document.getElementById('apicurio-chat-messages');
        msgs.appendChild(loading);
        msgs.scrollTop = msgs.scrollHeight;

        var endpoint = sessionId
            ? API_BASE + '/support/chat/' + sessionId
            : API_BASE + '/support/ask';

        fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ message: msg })
        })
            .then(function (r) { return r.json(); })
            .then(function (d) {
                loading.remove();
                addMsg(formatMd(d.answer), 'assistant', true);
            })
            .catch(function () {
                loading.remove();
                addMsg('Sorry, something went wrong. Please try again.', 'system');
            })
            .finally(function () {
                document.getElementById('apicurio-chat-send').disabled = false;
                input.focus();
            });
    }

    function addMsg(content, type, html) {
        var div = document.createElement('div');
        div.className = 'achat-msg ' + type;
        if (html) { div.innerHTML = content; } else { div.textContent = content; }
        var msgs = document.getElementById('apicurio-chat-messages');
        msgs.appendChild(div);
        msgs.scrollTop = msgs.scrollHeight;
    }

    function formatMd(text) {
        return ('<p>' + text
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
            .replace(/`([^`]+)`/g, '<code>$1</code>')
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/^### (.+)$/gm, '</p><h4>$1</h4><p>')
            .replace(/^## (.+)$/gm, '</p><h3>$1</h3><p>')
            .replace(/^\* (.+)$/gm, '<li>$1</li>')
            .replace(/^- (.+)$/gm, '<li>$1</li>')
            .replace(/^\d+\. (.+)$/gm, '<li>$1</li>')
            .replace(/\n\n/g, '</p><p>')
            .replace(/\n/g, '<br>') + '</p>')
            .replace(/<p><\/p>/g, '');
    }
})();
