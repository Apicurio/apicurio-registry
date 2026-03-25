(function () {
    'use strict';

    var API_BASE = document.currentScript.src.replace(/\/chat-widget\.js.*$/, '');
    var sessionId = null;
    var isOpen = false;

    // Inject styles - all rules use !important to resist host page CSS overrides (e.g. PatternFly)
    var style = document.createElement('style');
    style.textContent = [
        '#apicurio-chat-bubble{',
        '  position:fixed !important;bottom:24px !important;right:24px !important;',
        '  width:56px !important;height:56px !important;border-radius:50% !important;',
        '  background:#3b82f6 !important;color:#fff !important;border:none !important;',
        '  cursor:pointer !important;box-shadow:0 4px 16px rgba(59,130,246,.4) !important;',
        '  display:flex !important;align-items:center !important;justify-content:center !important;',
        '  z-index:99999 !important;transition:transform .2s,box-shadow .2s !important;',
        '  padding:0 !important;margin:0 !important;min-width:0 !important;min-height:0 !important;',
        '  line-height:1 !important;font-size:14px !important;letter-spacing:normal !important;',
        '  text-transform:none !important;outline:none !important;',
        '}',
        '#apicurio-chat-bubble:hover{',
        '  transform:scale(1.08) !important;box-shadow:0 6px 24px rgba(59,130,246,.5) !important;',
        '  background:#2563eb !important;',
        '}',
        '#apicurio-chat-bubble svg{',
        '  width:28px !important;height:28px !important;fill:currentColor !important;',
        '}',

        '#apicurio-chat-panel{',
        '  position:fixed !important;bottom:92px !important;right:24px !important;',
        '  width:400px !important;max-height:560px !important;height:560px !important;',
        '  background:#1e1e2e !important;border-radius:16px !important;',
        '  box-shadow:0 8px 40px rgba(0,0,0,.4) !important;z-index:99999 !important;',
        '  display:none !important;flex-direction:column !important;overflow:hidden !important;',
        '  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif !important;',
        '  border:1px solid #333 !important;margin:0 !important;padding:0 !important;',
        '  top:auto !important;left:auto !important;',
        '}',
        '#apicurio-chat-panel.open{display:flex !important}',

        '#apicurio-chat-header{',
        '  padding:16px !important;background:#252536 !important;border-bottom:1px solid #333 !important;',
        '  display:flex !important;align-items:center !important;justify-content:space-between !important;',
        '  margin:0 !important;min-height:0 !important;',
        '}',
        '#apicurio-chat-header h3{',
        '  margin:0 !important;font-size:15px !important;color:#e4e4e7 !important;font-weight:600 !important;',
        '  padding:0 !important;border:none !important;line-height:1.3 !important;',
        '}',
        '#apicurio-chat-header span{',
        '  font-size:12px !important;color:#71717a !important;display:block !important;margin-top:2px !important;',
        '}',
        '#apicurio-chat-close{',
        '  background:none !important;border:none !important;color:#71717a !important;',
        '  cursor:pointer !important;font-size:22px !important;padding:4px 8px !important;',
        '  border-radius:6px !important;line-height:1 !important;min-width:0 !important;',
        '  box-shadow:none !important;',
        '}',
        '#apicurio-chat-close:hover{background:#333 !important;color:#e4e4e7 !important}',

        '#apicurio-chat-messages{',
        '  flex:1 !important;overflow-y:auto !important;padding:16px !important;',
        '  display:flex !important;flex-direction:column !important;gap:12px !important;',
        '  margin:0 !important;background:#1e1e2e !important;',
        '}',
        '.achat-msg{',
        '  max-width:85% !important;padding:10px 14px !important;border-radius:12px !important;',
        '  font-size:14px !important;line-height:1.5 !important;word-wrap:break-word !important;',
        '  margin:0 !important;border:none !important;',
        '}',
        '.achat-msg.user{',
        '  align-self:flex-end !important;background:#3b82f6 !important;color:#fff !important;',
        '  border-bottom-right-radius:4px !important;',
        '}',
        '.achat-msg.assistant{',
        '  align-self:flex-start !important;background:#2a2a3c !important;color:#e4e4e7 !important;',
        '  border-bottom-left-radius:4px !important;',
        '}',
        '.achat-msg.system{',
        '  align-self:center !important;color:#71717a !important;font-size:13px !important;',
        '  font-style:italic !important;background:none !important;padding:4px !important;',
        '}',
        '.achat-msg.assistant pre{',
        '  background:#18181b !important;padding:8px 10px !important;border-radius:6px !important;',
        '  overflow-x:auto !important;margin:6px 0 !important;font-size:12px !important;',
        '}',
        '.achat-msg.assistant code{',
        '  font-family:"Fira Code",Consolas,monospace !important;font-size:12px !important;',
        '}',
        '.achat-msg.assistant p{margin:6px 0 !important}',
        '.achat-msg.assistant ul,.achat-msg.assistant ol{margin:6px 0 6px 16px !important}',
        '.achat-msg.assistant li{margin:3px 0 !important}',
        '.achat-msg.assistant strong{color:#93c5fd !important}',

        '.achat-loading span{',
        '  display:inline-block !important;width:7px !important;height:7px !important;',
        '  background:#60a5fa !important;border-radius:50% !important;',
        '  animation:achat-bounce 1.4s infinite ease-in-out !important;',
        '}',
        '.achat-loading span:nth-child(1){animation-delay:-.32s !important}',
        '.achat-loading span:nth-child(2){animation-delay:-.16s !important}',
        '@keyframes achat-bounce{0%,80%,100%{transform:scale(0)}40%{transform:scale(1)}}',

        '#apicurio-chat-input{',
        '  padding:12px !important;border-top:1px solid #333 !important;',
        '  display:flex !important;gap:8px !important;background:#1e1e2e !important;',
        '  margin:0 !important;',
        '}',
        '#apicurio-chat-input input{',
        '  flex:1 !important;padding:10px 12px !important;border:1px solid #333 !important;',
        '  border-radius:8px !important;background:#18181b !important;color:#e4e4e7 !important;',
        '  font-size:14px !important;outline:none !important;margin:0 !important;',
        '  height:auto !important;line-height:1.4 !important;box-shadow:none !important;',
        '  min-height:0 !important;width:auto !important;',
        '}',
        '#apicurio-chat-input input:focus{border-color:#3b82f6 !important}',
        '#apicurio-chat-input input::placeholder{color:#555 !important}',
        '#apicurio-chat-input button{',
        '  padding:10px 16px !important;background:#3b82f6 !important;color:#fff !important;',
        '  border:none !important;border-radius:8px !important;font-size:14px !important;',
        '  cursor:pointer !important;transition:background .2s !important;',
        '  min-width:0 !important;line-height:1.4 !important;box-shadow:none !important;',
        '  text-transform:none !important;letter-spacing:normal !important;',
        '}',
        '#apicurio-chat-input button:hover{background:#2563eb !important}',
        '#apicurio-chat-input button:disabled{background:#333 !important;cursor:not-allowed !important}',

        '@media(max-width:480px){',
        '  #apicurio-chat-panel{',
        '    width:calc(100vw - 16px) !important;height:calc(100vh - 120px) !important;',
        '    right:8px !important;bottom:80px !important;border-radius:12px !important;',
        '  }',
        '}'
    ].join('\n');
    document.head.appendChild(style);

    // Chat bubble
    var bubble = document.createElement('button');
    bubble.id = 'apicurio-chat-bubble';
    bubble.title = 'Apicurio Registry Support';
    bubble.setAttribute('type', 'button');
    bubble.innerHTML = '<svg viewBox="0 0 24 24"><path d="M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm0 14H6l-2 2V4h16v12z"/></svg>';
    bubble.onclick = togglePanel;

    // Chat panel
    var panel = document.createElement('div');
    panel.id = 'apicurio-chat-panel';
    panel.innerHTML = [
        '<div id="apicurio-chat-header">',
        '  <div><h3>Apicurio Registry Support</h3><span>AI-powered assistant</span></div>',
        '  <button type="button" id="apicurio-chat-close">&times;</button>',
        '</div>',
        '<div id="apicurio-chat-messages">',
        '  <div class="achat-msg system">Ask me anything about Apicurio Registry!</div>',
        '</div>',
        '<div id="apicurio-chat-input">',
        '  <input type="text" placeholder="Ask about Apicurio Registry..." id="apicurio-chat-text">',
        '  <button type="button" id="apicurio-chat-send">Send</button>',
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
