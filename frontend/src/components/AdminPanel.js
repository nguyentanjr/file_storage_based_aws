import React, { useEffect, useState } from 'react';
import { adminAPI } from '../services/api';

const bytesToGB = (bytes) => {
  if (!bytes || bytes <= 0) return 0;
  return bytes / (1024 * 1024 * 1024);
};

const formatBytes = (bytes) => {
  if (bytes == null) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = bytes;
  let unit = 0;
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024;
    unit++;
  }
  return `${size.toFixed(2)} ${units[unit]}`;
};

function AdminPanel() {
  const [stats, setStats] = useState(null);
  const [users, setUsers] = useState([]);
  const [edits, setEdits] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [savingQuota, setSavingQuota] = useState(null);
  const [savingPerm, setSavingPerm] = useState(null);
  const [globalQuotaGb, setGlobalQuotaGb] = useState('1.00');
  const [savingGlobalQuota, setSavingGlobalQuota] = useState(false);

  const loadData = async () => {
    setLoading(true);
    setError('');
    try {
      const [statsRes, usersRes] = await Promise.all([
        adminAPI.getStats(5),
        adminAPI.getUsers(),
      ]);
      setStats(statsRes.data);
      const userList = Array.isArray(usersRes.data) ? usersRes.data : [];
      setUsers(userList);
      const initialEdits = {};
      userList.forEach((user) => {
        initialEdits[user.id] = {
          quotaGb: bytesToGB(user.storageQuota || 0).toFixed(2),
          create: user.create,
          read: user.read,
          write: user.write,
        };
      });
      setEdits(initialEdits);
      if (userList.length > 0) {
        setGlobalQuotaGb(bytesToGB(userList[0].storageQuota || 0).toFixed(2));
      }
    } catch (err) {
      console.error(err);
      setError(err.response?.data?.message || 'Failed to load admin data');
    } finally {
      setLoading(false);
    }
  };

  const handleGlobalQuotaUpdate = async () => {
    const quotaGb = parseFloat(globalQuotaGb);
    if (isNaN(quotaGb) || quotaGb <= 0) {
      alert('Quota must be greater than 0');
      return;
    }
    setSavingGlobalQuota(true);
    try {
      await adminAPI.updateAllQuota({ storageQuotaGb: quotaGb });
      await loadData();
      alert('All user quotas updated');
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to update quotas');
    } finally {
      setSavingGlobalQuota(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleEditChange = (userId, field, value) => {
    setEdits((prev) => ({
      ...prev,
      [userId]: {
        ...prev[userId],
        [field]: value,
      },
    }));
  };

  const handleSaveQuota = async (userId) => {
    const quotaGb = parseFloat(edits[userId]?.quotaGb);
    if (isNaN(quotaGb) || quotaGb <= 0) {
      alert('Quota must be greater than 0');
      return;
    }
    setSavingQuota(userId);
    try {
      await adminAPI.updateQuota(userId, { storageQuotaGb: quotaGb });
      await loadData();
      alert('Quota updated');
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to update quota');
    } finally {
      setSavingQuota(null);
    }
  };

  const handleSavePermissions = async (userId) => {
    const edit = edits[userId];
    setSavingPerm(userId);
    try {
      await adminAPI.updatePermissions(userId, {
        create: edit.create,
        read: edit.read,
        write: edit.write,
      });
      await loadData();
      alert('Permissions updated');
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to update permissions');
    } finally {
      setSavingPerm(null);
    }
  };

  if (loading) {
    return (
      <div className="card admin-panel">
        <h3>Admin Panel</h3>
        <p>Loading admin data...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="card admin-panel">
        <h3>Admin Panel</h3>
        <p className="error-text">{error}</p>
        <button className="btn btn-secondary" onClick={loadData}>
          Retry
        </button>
      </div>
    );
  }

  return (
    <div className="admin-panel card">
      <div className="admin-panel-header">
        <h3>Admin Panel</h3>
        <button className="btn btn-secondary btn-small" onClick={loadData}>
          Refresh
        </button>
      </div>

      {stats && (
        <div className="admin-stats">
          <div className="stat-card">
            <span className="stat-label">Total Users</span>
            <span className="stat-value">{stats.totalUsers}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Storage Used</span>
            <span className="stat-value">{formatBytes(stats.totalStorageUsed)}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Total Quota</span>
            <span className="stat-value">{formatBytes(stats.totalStorageQuota)}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Overall Usage</span>
            <span className="stat-value">
              {stats.usagePercentage ? stats.usagePercentage.toFixed(2) : '0.00'}%
            </span>
          </div>
        </div>
      )}

      <div className="global-quota card-light">
        <h4>Global Storage Quota</h4>
        <div className="global-quota-controls">
          <input
            type="number"
            className="form-input small"
            value={globalQuotaGb}
            onChange={(e) => setGlobalQuotaGb(e.target.value)}
            min="0.1"
            step="0.1"
          />
          <span>GB per user</span>
          <button
            className="btn btn-primary btn-small"
            onClick={handleGlobalQuotaUpdate}
            disabled={savingGlobalQuota}
          >
            {savingGlobalQuota ? 'Updating...' : 'Apply to All'}
          </button>
        </div>
      </div>

      {stats?.topConsumers?.length > 0 && (
        <div className="top-consumers">
          <h4>Top Users by Storage</h4>
          <ul>
            {stats.topConsumers.map((u) => (
              <li key={u.id}>
                <strong>{u.username}</strong> – {formatBytes(u.storageUsed)} /{' '}
                {formatBytes(u.storageQuota)} (
                {u.usagePercentage ? u.usagePercentage.toFixed(1) : '0'}%)
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="admin-users">
        <h4>User Management</h4>
        <div className="admin-table-wrapper">
          <table className="admin-table">
            <thead>
              <tr>
                <th>User</th>
                <th>Role</th>
                <th>Storage</th>
                <th>Quota (GB)</th>
                <th>Permissions</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => {
                const edit = edits[user.id] || {};
                const used = user.storageUsed || 0;
                const quota = user.storageQuota || 0;
                const usagePercent = quota > 0 ? (used * 100) / quota : 0;
                return (
                  <tr key={user.id}>
                    <td>
                      <div className="user-info">
                        <strong>{user.username}</strong>
                        {user.email && <span>{user.email}</span>}
                      </div>
                    </td>
                    <td>{user.role}</td>
                    <td>
                      {formatBytes(used)} / {formatBytes(quota)}
                      <div className="mini-bar">
                        <div
                          className="mini-bar-fill"
                          style={{ width: `${Math.min(usagePercent, 100)}%` }}
                        ></div>
                      </div>
                    </td>
                    <td>
                      <input
                        type="number"
                        className="form-input small"
                        value={edit.quotaGb || ''}
                        onChange={(e) => handleEditChange(user.id, 'quotaGb', e.target.value)}
                        min="0.1"
                        step="0.1"
                      />
                      <button
                        className="btn btn-primary btn-small"
                        onClick={() => handleSaveQuota(user.id)}
                        disabled={savingQuota === user.id}
                      >
                        {savingQuota === user.id ? 'Saving...' : 'Update'}
                      </button>
                    </td>
                    <td>
                      <label>
                        <input
                          type="checkbox"
                          checked={edit.create || false}
                          onChange={(e) => handleEditChange(user.id, 'create', e.target.checked)}
                        />
                        Create
                      </label>
                      <label>
                        <input
                          type="checkbox"
                          checked={edit.write || false}
                          onChange={(e) => handleEditChange(user.id, 'write', e.target.checked)}
                        />
                        Write
                      </label>
                      <label>
                        <input
                          type="checkbox"
                          checked={edit.read || false}
                          onChange={(e) => handleEditChange(user.id, 'read', e.target.checked)}
                        />
                        Read
                      </label>
                    </td>
                    <td>
                      <button
                        className="btn btn-secondary btn-small"
                        onClick={() => handleSavePermissions(user.id)}
                        disabled={savingPerm === user.id}
                      >
                        {savingPerm === user.id ? 'Saving...' : 'Save Permissions'}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

export default AdminPanel;

