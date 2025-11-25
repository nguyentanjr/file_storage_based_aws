import React, { useState } from 'react';
import { FaCloudUploadAlt, FaTimes } from 'react-icons/fa';
import { fileAPI } from '../services/api';
import './FileUpload.css';

function FileUpload({ currentFolderId, onUploadSuccess }) {
  const [selectedFiles, setSelectedFiles] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState({});
  const [overallProgress, setOverallProgress] = useState(0);

  const handleFileSelect = (e) => {
    const files = Array.from(e.target.files);
    if (files.length > 0) {
      setSelectedFiles(prevFiles => [...prevFiles, ...files]);
    }
  };

  const removeFile = (index) => {
    setSelectedFiles(prevFiles => prevFiles.filter((_, i) => i !== index));
  };

  const uploadSingleFile = async (file, index) => {
    try {
      // Update progress for this file
      setUploadProgress(prev => ({ ...prev, [index]: 10 }));

      // Step 1: Get presigned upload URL from backend
      const urlResponse = await fileAPI.generateUploadUrl(
        file.name,
        file.size,
        currentFolderId
      );
      
      const { uploadUrl, fileId } = urlResponse.data;
      setUploadProgress(prev => ({ ...prev, [index]: 30 }));

      // Step 2: Upload directly to S3 using presigned URL
      const uploadResponse = await fileAPI.uploadToS3(file, uploadUrl);
      
      if (!uploadResponse.ok) {
        throw new Error(`Failed to upload ${file.name} to S3`);
      }

      setUploadProgress(prev => ({ ...prev, [index]: 70 }));

      // Step 3: Confirm upload with backend
      await fileAPI.confirmUpload(fileId, file.type);
      
      setUploadProgress(prev => ({ ...prev, [index]: 100 }));
      return { success: true, fileName: file.name };
    } catch (err) {
      setUploadProgress(prev => ({ ...prev, [index]: -1 })); // -1 indicates error
      return { success: false, fileName: file.name, error: err.message };
    }
  };

  const handleUpload = async () => {
    if (selectedFiles.length === 0) return;

    setUploading(true);
    setUploadProgress({});
    setOverallProgress(0);

    const results = [];
    let completed = 0;

    // Upload files sequentially to avoid overwhelming the server
    for (let i = 0; i < selectedFiles.length; i++) {
      const result = await uploadSingleFile(selectedFiles[i], i);
      results.push(result);
      completed++;
      setOverallProgress(Math.round((completed / selectedFiles.length) * 100));
    }

    // Show summary
    const successful = results.filter(r => r.success).length;
    const failed = results.filter(r => !r.success).length;

    if (failed > 0) {
      const failedFiles = results.filter(r => !r.success).map(r => r.fileName).join(', ');
      alert(`Upload completed: ${successful} succeeded, ${failed} failed.\nFailed files: ${failedFiles}`);
    } else {
      alert(`All ${successful} files uploaded successfully!`);
    }

    // Reset state
    setTimeout(() => {
      setSelectedFiles([]);
      setUploading(false);
      setUploadProgress({});
      setOverallProgress(0);
      onUploadSuccess();
      document.getElementById('file-input').value = '';
    }, 1000);
  };

  return (
    <div className="file-upload-container">
      <div className="upload-area">
        <FaCloudUploadAlt className="upload-icon" />
        <h3>Upload Files</h3>
        <p>Select one or multiple files to upload to your cloud storage</p>
        
        <input
          id="file-input"
          type="file"
          multiple
          onChange={handleFileSelect}
          disabled={uploading}
          style={{ display: 'none' }}
        />
        
        <label htmlFor="file-input" className="btn btn-primary">
          Choose Files
        </label>

        {/* Selected Files */}
        {selectedFiles.length > 0 && (
          <div className="selected-files">
            <p><strong>{selectedFiles.length} file(s) selected:</strong></p>
            <div className="files-list">
              {selectedFiles.map((file, index) => (
                <div key={index} className="file-item">
                  <div className="file-info">
                    <span className="file-name">{file.name}</span>
                    <span className="file-size">({(file.size / 1024 / 1024).toFixed(2)} MB)</span>
                  </div>
                  <div className="file-actions">
                    {uploading && uploadProgress[index] !== undefined && (
                      <span className="upload-status">
                        {uploadProgress[index] === -1 ? '❌' : 
                         uploadProgress[index] === 100 ? '✅' : 
                         `${uploadProgress[index]}%`}
                      </span>
                    )}
                    {!uploading && (
                      <button 
                        className="btn-remove"
                        onClick={() => removeFile(index)}
                        title="Remove file"
                      >
                        <FaTimes />
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
            
            <button
              className="btn btn-success upload-btn"
              onClick={handleUpload}
              disabled={uploading}
            >
              {uploading ? `Uploading... ${overallProgress}%` : `Upload ${selectedFiles.length} File(s)`}
            </button>
          </div>
        )}

        {uploading && (
          <div className="progress-bar">
            <div className="progress-fill" style={{ width: `${overallProgress}%` }}></div>
          </div>
        )}
      </div>
    </div>
  );
}

export default FileUpload;

