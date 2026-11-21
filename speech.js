const reply = document.querySelector('#reply');
const speak = document.querySelector('#speak');
const stop = document.querySelector('#stop');
const speechStatus = document.querySelector('#status');
const speech = window.speechSynthesis;
let current = null;

function updateButtons() {
  speak.disabled = !speech || !reply.value.trim() || current !== null;
  stop.disabled = current === null;
}

function finish(message) {
  current = null;
  speechStatus.textContent = message;
  updateButtons();
}

reply.addEventListener('input', updateButtons);

speak.addEventListener('click', () => {
  const utterance = new SpeechSynthesisUtterance(reply.value.trim());
  const voices = speech.getVoices().filter(({ localService }) => localService);
  const language = ({ lang }) => lang.toLowerCase().replaceAll('_', '-');
  const voice = voices.find(candidate => language(candidate) === 'zh-tw')
    ?? voices.find(candidate => language(candidate).split('-')[0] === 'zh');
  if (!voice) {
    finish('找不到本機中文語音。請確認系統已安裝中文語音，或換用支援的瀏覽器。');
    return;
  }
  utterance.voice = voice;
  utterance.lang = voice.lang;

  utterance.onend = () => {
    if (current === utterance) finish('朗讀完成。');
  };
  utterance.onerror = () => {
    if (current === utterance) finish('無法朗讀。請確認瀏覽器與系統語音設定。');
  };

  current = utterance;
  speechStatus.textContent = '朗讀中。';
  updateButtons();
  speech.speak(utterance);
});

stop.addEventListener('click', () => {
  finish('已停止朗讀。');
  speech.cancel();
});

if (!speech) speechStatus.textContent = '目前的瀏覽器未提供朗讀功能。';
updateButtons();
