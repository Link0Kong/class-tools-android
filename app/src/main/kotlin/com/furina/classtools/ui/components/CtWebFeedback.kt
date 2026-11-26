package com.furina.classtools.ui.components

/** 站内 WebView 的提示留在页面的前景层，并和原生提示使用相同配色。 */
internal const val EmbeddedFeedbackScript = """
    (() => {
      if (document.getElementById('ct-feedback-style')) return;
      const style = document.createElement('style');
      style.id = 'ct-feedback-style';
      style.textContent = `
        html body #toast {
          position: fixed; inset: 8px auto auto 50%; z-index: 2147483647;
          box-sizing: border-box; width: max-content; max-width: min(480px, calc(100% - 32px));
          background: #1C2629; color: #F4F6F6; border: 0; border-radius: 16px;
          padding: 12px 16px; font-size: .875rem; line-height: 1.4; text-align: start;
          overflow-wrap: anywhere; pointer-events: none; transform: translateX(-50%);
        }
        html[data-appearance=dark] body #toast, html[data-theme=dark] body #toast {
          background: #ECF1F6; color: #18212C;
        }
      `;
      document.head.append(style);
    })();
"""
