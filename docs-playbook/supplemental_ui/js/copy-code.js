;(function () {
  'use strict'

  var codeBlocks = document.querySelectorAll('.doc pre.highlight')

  if (!codeBlocks.length) return

  codeBlocks.forEach(function (codeBlock) {
    var button = document.createElement('button')

    button.className = 'copy-code-button'
    button.type = 'button'
    button.textContent = 'Copy'

    codeBlock.appendChild(button)

    button.addEventListener('click', function () {
      var code = codeBlock.querySelector('code')

      if (!code) return

      navigator.clipboard.writeText(code.textContent).then(function () {
        button.textContent = 'Copied!'

        setTimeout(function () {
          button.textContent = 'Copy'
        }, 1500)
      })
    })
  })
})()