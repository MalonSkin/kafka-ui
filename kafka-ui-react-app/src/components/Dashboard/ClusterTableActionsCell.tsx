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


interface ClusterTableActionsCellProps {
  row: Row<Cluster>;
  refreshClusters: () => void;
}

const ClusterTableActionsCell: React.FC<ClusterTableActionsCellProps> = ({ row, refreshClusters }) => {
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
        title: 'Error deleting application config',
        message: 'There was an error deleting the application config',
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
        <DialogTitle>确认删除</DialogTitle>
        <DialogContent>
          <p>您确定要删除集群【{name}】吗？此操作不可逆。</p>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog} color="primary">
            取消
          </Button>
          <Button onClick={handleConfirmDelete} color="error">
            确认删除
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default ClusterTableActionsCell;
