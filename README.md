# ☁️ ValetKey Cloud Storage

A modern, high-performance cloud storage application built with Spring Boot and React. Features direct S3 uploads, folder management, public file sharing, and comprehensive admin panel.

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18.2.0-blue.svg)](https://reactjs.org/)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Latest-blue.svg)](https://www.postgresql.org/)
[![AWS S3](https://img.shields.io/badge/AWS-S3-orange.svg)](https://aws.amazon.com/s3/)

## ✨ Features

### Core Features
- 📁 **Folder Management**: Create nested folders, navigate with breadcrumbs
- 📤 **Direct S3 Upload**: Presigned URLs for fast, direct client-to-S3 uploads
- 📥 **File Download**: Secure download URLs with expiration
- 🔍 **Advanced Search**: Search files by name, type, size with filters
- 🔗 **Public Sharing**: Generate shareable links for files
- 💾 **Storage Quota**: Per-user storage limits with real-time tracking
- 👥 **User Management**: Role-based access control (Admin/User)
- 🔐 **Authentication**: Session-based authentication with Spring Security

### Admin Panel
- 📊 **System Statistics**: Total users, storage usage, quota overview
- 👤 **User Management**: View all users, update permissions
- 💾 **Quota Management**: Set storage quotas per user or globally
- 📈 **Top Consumers**: Identify users with highest storage usage
- ⚙️ **Permission Control**: Manage create, read, write permissions

### Performance Optimizations
- ⚡ **Async Processing**: Asynchronous upload URL generation
- 🚀 **Connection Pooling**: Optimized HikariCP configuration
- 💨 **Caching**: Spring Cache for storage quota and folder tree
- 🔄 **Bulk Operations**: Efficient bulk delete, move, download
- 📊 **Load Balancing Ready**: Configured for high concurrency

### Backup & Monitoring
- 🔄 **Automated Backup**: AWS SQS + Lambda for backup processing
- 📊 **CloudWatch Metrics**: Real-time monitoring and metrics
- 🔔 **Error Tracking**: Comprehensive error logging


## 🛠️ Tech Stack

### Backend
- **Spring Boot 3.2.0** - Java framework
- **Spring Security** - Authentication & authorization
- **Spring Data JPA** - Database access
- **HikariCP** - Connection pooling
- **PostgreSQL** - Relational database
- **AWS SDK** - S3, SQS, CloudWatch integration
- **IBM COS SDK** - IBM Cloud Object Storage

### Frontend
- **React 18.2.0** - UI framework
- **React Router** - Routing
- **Axios** - HTTP client
- **React Icons** - Icon library

### Infrastructure
- **AWS S3** - Object storage
- **AWS SQS** - Message queue
- **AWS Lambda** - Serverless backup processing
- **AWS CloudWatch** - Monitoring & metrics
- **IBM COS** - Secondary backup storage

## 📋 Prerequisites

- Java 17+
- Node.js 16+ and npm
- PostgreSQL 12+
- Maven 3.6+
- AWS Account (for S3, SQS, Lambda, CloudWatch)
- IBM Cloud Account (optional, for backup)

## 🚀 Quick Start

### 1. Clone Repository

```bash
git clone https://github.com/yourusername/valetKeyCloud.git
cd valetKeyCloud
```

### 2. Backend Setup

#### Configure Database

Create `src/main/resources/application.properties` from `application.properties.example`:

```bash
cp src/main/resources/application.properties.example src/main/resources/application.properties
```

Edit `application.properties` with your database credentials:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/valetkey
spring.datasource.username=your_username
spring.datasource.password=your_password
```

#### Configure AWS

Set environment variables or update `application.properties`:

```bash
export AWS_ACCESS_KEY_ID=your_access_key
export AWS_SECRET_ACCESS_KEY=your_secret_key
export AWS_REGION=ap-southeast-1
export AWS_S3_BUCKET_NAME=your-bucket-name
export AWS_SQS_QUEUE_URL=https://sqs.region.amazonaws.com/account/queue-name
```

#### Run Backend

```bash
# Build project
mvn clean install

# Run application
mvn spring-boot:run
```

Backend will start at `http://localhost:8080`

### 3. Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Configure API endpoint (optional)
# Edit src/services/api.js if backend is not on localhost:8080

# Start development server
npm start
```

Frontend will start at `http://localhost:3000`

### 4. Create Admin User

After first run, create an admin user in database:

```sql
UPDATE "user" SET role = 'ROLE_ADMIN' WHERE username = 'admin';
```

Or register a new user and update role:

```sql
INSERT INTO "user" (username, password, role, storage_quota, storage_used, create, write, read)
VALUES ('admin', '$2a$08$...', 'ROLE_ADMIN', 1073741824, 0, true, true, true);
```

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `AWS_ACCESS_KEY_ID` | AWS access key | Required |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key | Required |
| `AWS_REGION` | AWS region | `ap-southeast-1` |
| `AWS_S3_BUCKET_NAME` | S3 bucket name | Required |
| `AWS_SQS_QUEUE_URL` | SQS queue URL | Required |
| `IBM_COS_ACCESS_KEY_ID` | IBM COS access key | Optional |
| `IBM_COS_SECRET_ACCESS_KEY` | IBM COS secret key | Optional |
| `IBM_COS_BUCKET_NAME` | IBM COS bucket name | Optional |

### Database Configuration

```properties
spring.datasource.url=jdbc:postgresql://host:port/database
spring.datasource.username=username
spring.datasource.password=password

# Connection Pool Settings
spring.datasource.hikari.maximum-pool-size=50
spring.datasource.hikari.minimum-idle=10
spring.datasource.hikari.connection-timeout=30000
```

### Server Configuration

```properties
# Tomcat Thread Pool
server.tomcat.threads.max=300
server.tomcat.threads.min-spare=30
server.tomcat.max-connections=10000

# Session
server.servlet.session.timeout=30m
```

## 📚 API Documentation

### Authentication

```http
POST /api/login
Content-Type: application/json

{
  "username": "user",
  "password": "password"
}
```

### File Operations

```http
# Generate upload URL
POST /api/files/upload-url
{
  "fileName": "example.pdf",
  "fileSize": 1024000,
  "folderId": 1
}

# List files
GET /api/files/list?folderId=1&page=0&size=20

# Download file
GET /api/files/{fileId}/download

# Delete file
DELETE /api/files/{fileId}

# Search files
GET /api/files/search?query=example&page=0&size=20
```

### Admin Operations

```http
# Get all users
GET /admin/user-list

# Update user quota
PUT /admin/quota/{userId}
{
  "storageQuotaGb": 2.0
}

# Update permissions
POST /admin/permission/{userId}
{
  "create": true,
  "read": true,
  "write": true
}

# Get system stats
GET /admin/stats?top=5
```

See [API Documentation](./docs/API.md) for complete API reference.

## 🎯 Usage Examples

### Upload File

```javascript
import { fileAPI } from './services/api';

// 1. Generate presigned URL
const response = await fileAPI.generateUploadUrl('document.pdf', 1024000, folderId);
const { uploadUrl, fileId } = response.data;

// 2. Upload directly to S3
await fileAPI.uploadToS3(file, uploadUrl);

// 3. Confirm upload
await fileAPI.confirmUpload(fileId, file.type);
```

### Bulk Operations

```javascript
// Bulk delete
await fileAPI.bulkDelete([fileId1, fileId2, fileId3]);

// Bulk move
await fileAPI.bulkMove([fileId1, fileId2], targetFolderId);

// Bulk download (ZIP)
const response = await fileAPI.bulkDownload([fileId1, fileId2]);
window.open(response.data.downloadUrl);
```

## 🧪 Testing

### Load Testing

The project includes Locust load test scripts:

```bash
# Install Locust
pip install locust

# Run load test
locust -f scripts/locustfile.py --host=http://localhost:8080
```

### Performance Benchmarks

- **Upload URL Generation**: < 100ms (async)
- **File Upload**: Direct to S3 (bypasses server)
- **Concurrent Users**: Tested up to 500 users
- **Response Time**: P95 < 2s under load

## 📁 Project Structure

```
valetKeyCloud/
├── src/main/java/com/example/valetkey/
│   ├── config/          # Configuration classes
│   ├── controller/      # REST controllers
│   ├── model/           # Entity models
│   ├── repository/      # JPA repositories
│   ├── service/         # Business logic
│   └── lambda/          # AWS Lambda handlers
├── src/main/resources/
│   ├── application.properties.example
│   └── static/
├── frontend/
│   ├── src/
│   │   ├── components/  # React components
│   │   └── services/    # API clients
│   └── public/
├── docs/                # Documentation
└── scripts/              # Utility scripts
```

## 🔒 Security

- ✅ Session-based authentication
- ✅ Role-based access control (RBAC)
- ✅ Password encryption (BCrypt)
- ✅ CORS configuration
- ✅ SQL injection prevention (JPA)
- ✅ XSS protection
- ✅ Secure file upload validation

## 🚀 Deployment

### Production Build

```bash
# Backend
mvn clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar

# Frontend
cd frontend
npm run build
# Serve build/ directory with nginx or similar
```

### Docker (Optional)

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/demo-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Environment Setup

1. Set all required environment variables
2. Configure database connection
3. Set up AWS S3 bucket and SQS queue
4. Configure CORS for frontend domain
5. Set up reverse proxy (nginx) if needed

## 📊 Monitoring

### CloudWatch Metrics

- Upload success/failure rates
- Backup latency
- Storage usage trends
- API response times

### Actuator Endpoints

```http
GET /actuator/health
GET /actuator/metrics
GET /actuator/prometheus
```

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request



