import React, { useState } from 'react';
import { FaFolder, FaTrash, FaEdit, FaFolderOpen } from 'react-icons/fa';
import { folderAPI } from '../services/api';
import './FolderList.css';

function FolderList({ folders, onFolderUpdated, onFolderDeleted, onNavigate, allFolders = [] }) {
  const [renameModal, setRenameModal] = useState(null);
  const [moveModal, setMoveModal] = useState(null);
  const [deleteModal, setDeleteModal] = useState(null);
  const [newName, setNewName] = useState('');
  const [targetFolderId, setTargetFolderId] = useState('');
  const [deletingFolders, setDeletingFolders] = useState(new Set());
  const [renamingFolders, setRenamingFolders] = useState(new Set());
  const [movingFolders, setMovingFolders] = useState(new Set());

  const handleRename = async (folderId) => {
    if (!newName.trim()) return;
    
    setRenamingFolders(prev => new Set(prev).add(folderId));
    try {
      await folderAPI.rename(folderId, newName.trim());
      setRenameModal(null);
      setNewName('');
      onFolderUpdated();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to rename folder');
    } finally {
      setRenamingFolders(prev => {
        const next = new Set(prev);
        next.delete(folderId);
        return next;
      });
    }
  };

  const handleMove = async (folderId) => {
    setMovingFolders(prev => new Set(prev).add(folderId));
    try {
      await folderAPI.move(folderId, targetFolderId || null);
      setMoveModal(null);
      setTargetFolderId('');
      onFolderUpdated();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to move folder');
    } finally {
      setMovingFolders(prev => {
        const next = new Set(prev);
        next.delete(folderId);
        return next;
      });
    }
  };

  const handleDelete = async (folderId, folderName, deleteContents = false) => {
    const message = deleteContents
      ? `Delete "${folderName}" and all its contents? This action cannot be undone.`
      : `Delete "${folderName}"? (Folder must be empty)`;
    
    if (!window.confirm(message)) return;
    
    setDeletingFolders(prev => new Set(prev).add(folderId));
    try {
      await folderAPI.delete(folderId, deleteContents);
      setDeleteModal(null);
      onFolderDeleted();
      onFolderUpdated();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to delete folder');
    } finally {
      setDeletingFolders(prev => {
        const next = new Set(prev);
        next.delete(folderId);
        return next;
      });
    }
  };

  const openRenameModal = (folder) => {
    setNewName(folder.name);
    setRenameModal(folder.id);
  };

  const openMoveModal = (folder) => {
    setTargetFolderId('');
    setMoveModal(folder.id);
  };

  const openDeleteModal = (folder) => {
    setDeleteModal(folder.id);
  };

  if (!folders || folders.length === 0) {
    return null;
  }

  return (
    <div className="folder-list">
      <div className="folders-grid">
        {folders.map((folder) => (
          <div
            key={folder.id}
            className="folder-card"
          >
            <div 
              className="folder-card-main"
              onClick={() => onNavigate(folder.id)}
            >
              <FaFolder className="folder-icon" />
              <span className="folder-name" title={folder.name}>{folder.name}</span>
            </div>
            <div className="folder-actions">
              <button
                className="btn-icon"
                onClick={(e) => {
                  e.stopPropagation();
                  openRenameModal(folder);
                }}
                title="Rename"
                disabled={renamingFolders.has(folder.id)}
              >
                <FaEdit />
              </button>
              <button
                className="btn-icon"
                onClick={(e) => {
                  e.stopPropagation();
                  openMoveModal(folder);
                }}
                title="Move"
                disabled={movingFolders.has(folder.id)}
              >
                <FaFolderOpen />
              </button>
              <button
                className="btn-icon btn-danger"
                onClick={(e) => {
                  e.stopPropagation();
                  openDeleteModal(folder);
                }}
                title="Delete"
                disabled={deletingFolders.has(folder.id)}
              >
                <FaTrash />
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Rename Modal */}
      {renameModal && (
        <div className="modal-overlay" onClick={() => {
          setRenameModal(null);
          setNewName('');
        }}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Rename Folder</h3>
              <button className="modal-close" onClick={() => {
                setRenameModal(null);
                setNewName('');
              }}>×</button>
            </div>
            <div className="form-group">
              <label className="form-label">New name:</label>
              <input
                type="text"
                className="form-input"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                placeholder="Enter folder name"
                autoFocus
                onKeyPress={(e) => e.key === 'Enter' && handleRename(renameModal)}
              />
            </div>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => {
                setRenameModal(null);
                setNewName('');
              }}>
                Cancel
              </button>
              <button 
                className="btn btn-primary" 
                onClick={() => handleRename(renameModal)}
                disabled={renamingFolders.has(renameModal)}
              >
                {renamingFolders.has(renameModal) ? 'Renaming...' : 'Rename'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Move Modal */}
      {moveModal && (
        <div className="modal-overlay" onClick={() => {
          setMoveModal(null);
          setTargetFolderId('');
        }}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Move Folder</h3>
              <button className="modal-close" onClick={() => {
                setMoveModal(null);
                setTargetFolderId('');
              }}>×</button>
            </div>
            <div className="form-group">
              <label className="form-label">Select destination:</label>
              <select
                className="form-input"
                value={targetFolderId || ''}
                onChange={(e) => setTargetFolderId(e.target.value || null)}
              >
                <option value="">📁 Root (My Files)</option>
                {allFolders
                  .filter(f => f.id !== moveModal) // Exclude current folder
                  .map((folder) => (
                    <option key={folder.id} value={folder.id}>
                      📁 {folder.name} {folder.fullPath ? `(${folder.fullPath})` : ''}
                    </option>
                  ))}
              </select>
            </div>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => {
                setMoveModal(null);
                setTargetFolderId('');
              }}>
                Cancel
              </button>
              <button 
                className="btn btn-primary" 
                onClick={() => handleMove(moveModal)}
                disabled={movingFolders.has(moveModal)}
              >
                {movingFolders.has(moveModal) ? 'Moving...' : 'Move Here'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Modal */}
      {deleteModal && (
        <div className="modal-overlay" onClick={() => setDeleteModal(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Delete Folder</h3>
              <button className="modal-close" onClick={() => setDeleteModal(null)}>×</button>
            </div>
            <div className="form-group">
              <p>Are you sure you want to delete this folder?</p>
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '1rem' }}>
                <input
                  type="checkbox"
                  id="deleteContents"
                  onChange={(e) => {
                    // This will be handled in the delete function
                  }}
                />
                <span>Delete folder and all its contents</span>
              </label>
            </div>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => setDeleteModal(null)}>
                Cancel
              </button>
              <button 
                className="btn btn-danger" 
                onClick={() => {
                  const folder = folders.find(f => f.id === deleteModal);
                  const deleteContents = document.getElementById('deleteContents').checked;
                  handleDelete(deleteModal, folder?.name || 'Folder', deleteContents);
                }}
                disabled={deletingFolders.has(deleteModal)}
              >
                {deletingFolders.has(deleteModal) ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default FolderList;


