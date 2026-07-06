import React, { useMemo, useState } from 'react';
import { Cluster, ResourceType } from 'generated-sources';
import { Row } from '@tanstack/react-table';
import { clusterConfigPath } from 'lib/paths';
import { useGetUserInfo } from 'lib/hooks/api/roles';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Stack } from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import SettingsIcon from '@mui/icons-material/Settings';
import { Link } from 'react-router-dom';
import { useDeleteAppConfig } from 'lib/hooks/api/appConfig';
import { showAlert } from 'lib/errorHandling';
import { useTranslation } from 'react-i18next';


interface ClusterTableActionsCellProps {
  row: Row<Cluster>;
  refreshClusters: () => void;
}

const ClusterTableActionsCell: React.FC<ClusterTableActionsCellProps> = ({ row, refreshClusters }) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const { name } = row.original;
  const { data } = useGetUserInfo();
  const [openDialog, setOpenDialog] = useState(false);

  const hasPermissions = useMemo(() => {
    if (!data?.rbacEnabled) return true;
    return !!data?.userInfo?.permissions.some(
      (permission) => permission.resource === ResourceType.APPLICATIONCONFIG,
    );
  }, [data]);

  const handleDeleteClick = () => {
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
  };

  const deleteAppConfig = useDeleteAppConfig({ clusterName: name });

  const handleConfirmDelete = async () => {
    try {
      await deleteAppConfig.mutateAsync();
      setOpenDialog(false);
      refreshClusters();
    } catch (e) {
      showAlert('error', {
        id: 'app-config-delete-error',
        title: t('error.errorDeletingConfig'),
        message: t('error.errorDeletingConfig'),
      });
    }
  };

  return (
    <>
      <Stack direction="row" spacing={1}>
        <Link to={clusterConfigPath(name)}>
          <IconButton color="primary">
            <SettingsIcon />
          </IconButton>
        </Link>
        <IconButton color="error" onClick={handleDeleteClick}>
          <DeleteIcon />
        </IconButton>
      </Stack>

      <Dialog open={openDialog} onClose={handleCloseDialog}>
        <DialogTitle>{t('dashboard.confirmDeleteClusterTitle')}</DialogTitle>
        <DialogContent>
          <p>{t('dashboard.confirmDeleteClusterMessage', { name })}</p>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog} color="primary">
            {t('common.cancel')}
          </Button>
          <Button onClick={handleConfirmDelete} color="error">
            {t('dashboard.confirmDelete')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ClusterTableActionsCell;
