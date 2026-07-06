import { showAlert, showSuccessAlert } from 'lib/errorHandling';
import i18n from 'i18n/config';

const useDataSaver = (
  subject: string,
  data: Record<string, string> | string
) => {
  const copyToClipboard = () => {
    if (navigator.clipboard) {
      const str =
        typeof data === 'string' ? String(data) : JSON.stringify(data);
      navigator.clipboard.writeText(str);
      showSuccessAlert({
        id: subject,
        title: '',
        message: i18n.t('common.copySuccess'),
      });
    } else {
      showAlert('warning', {
        id: subject,
        title: i18n.t('common.warning'),
        message:
          i18n.t('common.clipboardUnavailable'),
      });
    }
  };
  const saveFile = () => {
    const blob = new Blob([data as BlobPart], { type: 'text/json' });
    const elem = window.document.createElement('a');
    elem.href = window.URL.createObjectURL(blob);
    elem.download = subject;
    document.body.appendChild(elem);
    elem.click();
    document.body.removeChild(elem);
  };

  return { copyToClipboard, saveFile };
};

export default useDataSaver;