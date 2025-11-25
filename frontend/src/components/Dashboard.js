import React, { useState, useEffect } from 'react';
import { FaFolder, FaPlus, FaSearch } from 'react-icons/fa';
import { fileAPI, folderAPI } from '../services/api';
import FileUpload from './FileUpload';
import FileList from './FileList';
import FolderList from './FolderList';
import AdminPanel from './AdminPanel';
import './Dashboard.css';

function Dashboard({ user, onLogout }) {
  const [files, setFiles] = useState([]);
  const [folders, setFolders] = useState([]);
  const [currentFolderId, setCurrentFolderId] = useState(null);
  const [breadcrumb, setBreadcrumb] = useState([]);
  const [storageInfo, setStorageInfo] = useState(null);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [showCreateFolder, setShowCreateFolder] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalItems, setTotalItems] = useState(0);
  const [pageSize] = useState(20);
  const [showFolderPicker, setShowFolderPicker] = useState(false);
  const [selectedTargetFolder, setSelectedTargetFolder] = useState(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const [showAdvancedSearch, setShowAdvancedSearch] = useState(false);
  const [searchFilters, setSearchFilters] = useState({
    fileType: '',
    minSize: '',
    maxSize: '',
  });
  const [searchResults, setSearchResults] = useState({ files: [], folders: [] });
  const [isSearching, setIsSearching] = useState(false);

  const isAdmin = user.role === 'ROLE_ADMIN';

  useEffect(() => {
    if (isAdmin) {
      setLoading(false);
      return;
    }
    loadData();
  }, [isAdmin, currentFolderId, currentPage]);

  const loadData = async () => {
    setLoading(true);
    try {
      // Load files with pagination
      const filesRes = await fileAPI.list(currentFolderId, currentPage, pageSize);
      setFiles(filesRes.data.files || []);
      setTotalPages(filesRes.data.totalPages || 0);
      setTotalItems(filesRes.data.totalItems || 0);

      // Load folders
      const foldersRes = await folderAPI.list(currentFolderId);
      const folderList = foldersRes.data.folders || [];

      // Load all folders for move operation
      const allFoldersRes = await folderAPI.getTree();
      const flatFolders = flattenFolders(allFoldersRes.data.tree || []);
      setFolders(flatFolders.length > 0 ? flatFolders : folderList);

      // Load breadcrumb
      const breadcrumbRes = await folderAPI.getBreadcrumb(currentFolderId);
      setBreadcrumb(breadcrumbRes.data.breadcrumb || []);

      // Load storage info
      const storageRes = await fileAPI.getStorageInfo();
      setStorageInfo(storageRes.data);
    } catch (err) {
      console.error('Failed to load data:', err);
    } finally {
      setLoading(false);
    }
  };

  const flattenFolders = (tree, result = []) => {
    tree.forEach(folder => {
      result.push(folder);
      if (folder.children && folder.children.length > 0) {
        flattenFolders(folder.children, result);
      }
    });
    return result;
  };

  const handleCreateFolder = async () => {
    if (!newFolderName.trim()) return;

    try {
      await folderAPI.create(newFolderName, currentFolderId);
      setShowCreateFolder(false);
      setNewFolderName('');
      loadData();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to create folder');
    }
  };

  const handleSearch = async () => {
    if (!searchQuery.trim() && !showAdvancedSearch) {
      loadData();
      setSearchResults({ files: [], folders: [] });
      setIsSearching(false);
      return;
    }

    setIsSearching(true);
    try {
      // Search both files and folders
      const [filesResponse, foldersResponse] = await Promise.all([
        showAdvancedSearch
          ? fileAPI.advancedSearch({
              query: searchQuery || undefined,
              folderId: currentFolderId || undefined,
              fileType: searchFilters.fileType || undefined,
              minSize: searchFilters.minSize ? parseInt(searchFilters.minSize) * 1024 * 1024 : undefined,
              maxSize: searchFilters.maxSize ? parseInt(searchFilters.maxSize) * 1024 * 1024 : undefined,
              page: currentPage,
              size: pageSize,
            })
          : fileAPI.search(searchQuery, currentPage, pageSize),
        folderAPI.search(searchQuery),
      ]);

      const files = filesResponse.data.files || [];
      const folders = foldersResponse.data.folders || [];

      setFiles(files);
      setTotalPages(filesResponse.data.totalPages || 0);
      setTotalItems(filesResponse.data.totalItems || 0);
      setSearchResults({ files, folders });
    } catch (err) {
      console.error('Search failed:', err);
      alert('Search failed');
    } finally {
      setIsSearching(false);
    }
  };

  const handleBulkDownload = async () => {
    if (selectedFiles.length === 0) return;

    setIsDownloading(true);
    try {
      const response = await fileAPI.bulkDownload(selectedFiles);
      // Open download URL in new tab
      window.open(response.data.downloadUrl, '_blank');
      alert(`Download started! ZIP file contains ${response.data.fileCount} file(s).`);
    } catch (err) {
      alert('Failed to generate bulk download: ' + (err.response?.data?.message || err.message));
    } finally {
      setIsDownloading(false);
    }
  };

  const handleNavigate = (folderId) => {
    setCurrentFolderId(folderId);
    setSearchQuery('');
    setCurrentPage(0); // Reset to first page when navigating
    setSelectedFiles([]); // Clear selection
  };

  const handleGoBack = () => {
    if (breadcrumb.length > 1) {
      // Go to parent folder
      const parentIndex = breadcrumb.length - 2;
      const parentFolder = breadcrumb[parentIndex];
      setCurrentFolderId(parentFolder.id);
      setCurrentPage(0);
    } else {
      // Go to root
      setCurrentFolderId(null);
      setCurrentPage(0);
    }
    setSelectedFiles([]);
  };

  const handleBulkDelete = async () => {
    if (selectedFiles.length === 0) return;
    if (!window.confirm(`Delete ${selectedFiles.length} file(s)?`)) return;

    setIsDeleting(true);
    try {
      await fileAPI.bulkDelete(selectedFiles);
      setSelectedFiles([]);
      loadData();
    } catch (err) {
      alert('Failed to delete files');
    } finally {
      setIsDeleting(false);
    }
  };


  const handleBulkMove = async (targetFolderId) => {
    if (selectedFiles.length === 0) return;

    try {
      await fileAPI.bulkMove(selectedFiles, targetFolderId);
      setSelectedFiles([]);
      loadData();
    } catch (err) {
      alert('Failed to move files');
    }
  };


  const toggleFileSelection = (fileId) => {
    setSelectedFiles(prev => 
      prev.includes(fileId) 
        ? prev.filter(id => id !== fileId)
        : [...prev, fileId]
    );
  };

  const selectAll = () => {
    setSelectedFiles(files.map(f => f.id));
  };

  const selectAllRecords = async () => {
    try {
      const response = await fileAPI.getAllIds(currentFolderId);
      const allFileIds = response.data.fileIds || [];
      setSelectedFiles(allFileIds);
    } catch (err) {
      console.error('Failed to get all file IDs:', err);
      alert('Failed to select all records');
    }
  };

  const deselectAll = () => {
    setSelectedFiles([]);
  };

  if (isAdmin) {
    return (
      <div className="app">
        <header className="header">
          <div className="header-content">
            <h1>☁️ Cloud Storage</h1>
            <div className="header-user">
              <span>{user.username} (Admin)</span>
              <button className="btn btn-secondary btn-small" onClick={onLogout}>
                Logout
              </button>
            </div>
          </div>
        </header>
        <div className="container">
          <AdminPanel />
        </div>
      </div>
    );
  }

  if (loading && !files.length) {
    return <div className="loading">Loading...</div>;
  }

  return (
    <div className="app">
      {/* Header */}
      <header className="header">
        <div className="header-content">
          <h1>☁️ Cloud Storage</h1>
          <div className="header-user">
            {storageInfo && (
              <div className="storage-info">
                {storageInfo.storageUsedFormatted} / {storageInfo.storageQuotaFormatted}
                <span className="storage-bar">
                  <span 
                    className="storage-fill" 
                    style={{ width: `${storageInfo.usagePercentage}%` }}
                  ></span>
                </span>
              </div>
            )}
            <span>{user.username}</span>
            <button className="btn btn-secondary btn-small" onClick={onLogout}>
              Logout
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <div className="container">
        {/* Breadcrumb with Back Button */}
        <div className="breadcrumb">
          <button className="btn btn-secondary btn-small" onClick={handleGoBack}>
            ← Back
          </button>
          {breadcrumb.map((item, index) => (
            <React.Fragment key={index}>
              <button
                className="breadcrumb-item"
                onClick={() => handleNavigate(item.id)}
              >
                {item.name}
              </button>
              {index < breadcrumb.length - 1 && <span> / </span>}
            </React.Fragment>
          ))}
        </div>

        {/* Advanced Search Panel */}
        {showAdvancedSearch && (
          <div className="card" style={{ marginBottom: '1rem', padding: '1rem' }}>
            <h4>Advanced Search Filters</h4>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
              <div>
                <label>File Type:</label>
                <select
                  className="form-input"
                  value={searchFilters.fileType}
                  onChange={(e) => setSearchFilters({ ...searchFilters, fileType: e.target.value })}
                >
                  <option value="">All Types</option>
                  <option value="image">Images</option>
                  <option value="video">Videos</option>
                  <option value="audio">Audio</option>
                  <option value="application/pdf">PDF</option>
                  <option value="text">Text</option>
                  <option value="application">Documents</option>
                </select>
              </div>
              <div>
                <label>Min Size (MB):</label>
                <input
                  type="number"
                  className="form-input"
                  placeholder="Min size"
                  value={searchFilters.minSize}
                  onChange={(e) => setSearchFilters({ ...searchFilters, minSize: e.target.value })}
                />
              </div>
              <div>
                <label>Max Size (MB):</label>
                <input
                  type="number"
                  className="form-input"
                  placeholder="Max size"
                  value={searchFilters.maxSize}
                  onChange={(e) => setSearchFilters({ ...searchFilters, maxSize: e.target.value })}
                />
              </div>
            </div>
            <div style={{ marginTop: '1rem' }}>
              <button className="btn btn-primary" onClick={handleSearch}>
                Search
              </button>
              <button 
                className="btn btn-secondary" 
                onClick={() => {
                  setSearchFilters({ fileType: '', minSize: '', maxSize: '' });
                  setSearchQuery('');
                  setShowAdvancedSearch(false);
                  loadData();
                }}
              >
                Clear Filters
              </button>
            </div>
          </div>
        )}

        {/* Toolbar */}
        <div className="toolbar">
          <div className="toolbar-left">
            <button
              className="btn btn-primary"
              onClick={() => setShowCreateFolder(true)}
            >
              <FaPlus /> New Folder
            </button>
          </div>
          
          {selectedFiles.length > 0 && (
            <div className="bulk-actions">
              <span>{selectedFiles.length} selected</span>
              <button 
                className="btn btn-primary btn-small" 
                onClick={handleBulkDownload}
                disabled={isDownloading}
              >
                {isDownloading ? '⏳ Preparing...' : `📥 Download ZIP (${selectedFiles.length})`}
              </button>
              <button className="btn btn-secondary btn-small" onClick={() => setShowFolderPicker(true)}>
                📁 Move
              </button>
              <button 
                className="btn btn-danger btn-small" 
                onClick={handleBulkDelete}
                disabled={isDeleting}
              >
                {isDeleting ? '⏳ Deleting...' : `🗑️ Delete (${selectedFiles.length})`}
              </button>
              <button className="btn btn-secondary btn-small" onClick={deselectAll}>
                ✖️ Clear
              </button>
            </div>
          )}
          
          <div className="toolbar-right">
            <div className="search-box">
              <input
                type="text"
                placeholder="Search files..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyPress={(e) => e.key === 'Enter' && handleSearch()}
              />
              <button onClick={handleSearch}>
                <FaSearch />
              </button>
              <button 
                className="btn btn-secondary btn-small"
                onClick={() => setShowAdvancedSearch(!showAdvancedSearch)}
                title="Advanced Search"
              >
                🔍
              </button>
            </div>
          </div>
        </div>

        {/* Folders */}
        {!isSearching && folders.length > 0 && !searchQuery && (
          <div className="card" style={{ marginBottom: '1rem' }}>
            <div className="card-header">
              Folders ({folders.length})
            </div>
            <FolderList
              folders={folders}
              onFolderUpdated={loadData}
              onFolderDeleted={loadData}
              onNavigate={handleNavigate}
              allFolders={folders}
            />
          </div>
        )}

        {/* Search Results - Folders */}
        {isSearching && searchResults.folders.length > 0 && (
          <div className="card" style={{ marginBottom: '1rem' }}>
            <div className="card-header">
              Search Results - Folders ({searchResults.folders.length})
            </div>
            <FolderList
              folders={searchResults.folders}
              onFolderUpdated={() => {
                loadData();
                setSearchQuery('');
                setSearchResults({ files: [], folders: [] });
              }}
              onFolderDeleted={() => {
                loadData();
                setSearchQuery('');
                setSearchResults({ files: [], folders: [] });
              }}
              onNavigate={(folderId) => {
                handleNavigate(folderId);
                setSearchQuery('');
                setSearchResults({ files: [], folders: [] });
              }}
              allFolders={folders}
            />
          </div>
        )}

        {/* Upload */}
        <FileUpload 
          currentFolderId={currentFolderId} 
          onUploadSuccess={loadData}
        />

        {user.role === 'ROLE_ADMIN' && (
          <AdminPanel />
        )}

        {/* Files */}
        <div className="card">
          <div className="card-header">
            {isSearching ? 'Search Results - Files' : 'Files'}
            {files.length > 0 && (
              <>
                <button className="btn btn-secondary btn-small" onClick={selectAll}>
                  Select All (Page)
                </button>
                {totalItems > files.length && (
                  <button className="btn btn-primary btn-small" onClick={selectAllRecords}>
                    Select All Records ({totalItems})
                  </button>
                )}
              </>
            )}
            <span className="file-count">
              ({totalItems} total)
            </span>
          </div>
          <FileList
            files={files}
            folders={folders}
            selectedFiles={selectedFiles}
            onToggleSelection={toggleFileSelection}
            onFileDeleted={loadData}
            onFileUpdated={loadData}
          />
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="pagination">
            <button
              className="btn btn-secondary"
              onClick={() => setCurrentPage(Math.max(0, currentPage - 1))}
              disabled={currentPage === 0}
            >
              ← Previous
            </button>
            <span>
              Page {currentPage + 1} of {totalPages} ({totalItems} items)
            </span>
            <button
              className="btn btn-secondary"
              onClick={() => setCurrentPage(Math.min(totalPages - 1, currentPage + 1))}
              disabled={currentPage >= totalPages - 1}
            >
              Next →
            </button>
          </div>
        )}
      </div>

      {/* Create Folder Modal */}
      {showCreateFolder && (
        <div className="modal-overlay" onClick={() => setShowCreateFolder(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Create New Folder</h3>
              <button className="modal-close" onClick={() => setShowCreateFolder(false)}>×</button>
            </div>
            <div className="form-group">
              <label className="form-label">Folder Name:</label>
              <input
                type="text"
                className="form-input"
                value={newFolderName}
                onChange={(e) => setNewFolderName(e.target.value)}
                placeholder="Enter folder name"
                autoFocus
                onKeyPress={(e) => e.key === 'Enter' && handleCreateFolder()}
              />
            </div>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => setShowCreateFolder(false)}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={handleCreateFolder}>
                Create
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Folder Picker Modal for Bulk Move */}
      {showFolderPicker && (
        <div className="modal-overlay" onClick={() => setShowFolderPicker(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">Select Destination Folder</h3>
              <button className="modal-close" onClick={() => setShowFolderPicker(false)}>×</button>
            </div>
            <div className="form-group">
              <label className="form-label">Choose folder:</label>
              <select
                className="form-input"
                value={selectedTargetFolder || ''}
                onChange={(e) => setSelectedTargetFolder(e.target.value || null)}
              >
                <option value="">📁 Root (My Files)</option>
                {folders.map((folder) => (
                  <option key={folder.id} value={folder.id}>
                    📁 {folder.name} {folder.fullPath ? `(${folder.fullPath})` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="modal-actions">
              <button className="btn btn-secondary" onClick={() => {
                setShowFolderPicker(false);
                setSelectedTargetFolder(null);
              }}>
                Cancel
              </button>
              <button className="btn btn-primary" onClick={() => {
                handleBulkMove(selectedTargetFolder);
                setShowFolderPicker(false);
                setSelectedTargetFolder(null);
              }}>
                Move Here
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default Dashboard;

