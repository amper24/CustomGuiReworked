# Git Setup Instructions

This document provides step-by-step instructions for setting up the CustomGuiReworked project on GitHub.

## Prerequisites

- Git installed on your system
- GitHub account
- Basic knowledge of Git commands

## Step 1: Initialize Git Repository

```bash
# Navigate to your project directory
cd /path/to/CustomGuiReworked

# Initialize Git repository
git init

# Add all files to staging
git add .

# Create initial commit
git commit -m "Initial commit: CustomGuiReworked plugin with API"
```

## Step 2: Create GitHub Repository

1. Go to [GitHub](https://github.com)
2. Click the "+" icon in the top right corner
3. Select "New repository"
4. Fill in the repository details:
   - **Repository name**: `CustomGuiReworked`
   - **Description**: `Advanced Custom GUI System for Minecraft Servers`
   - **Visibility**: Choose Public or Private
   - **Initialize with**: Don't initialize (we already have files)
5. Click "Create repository"

## Step 3: Connect Local Repository to GitHub

```bash
# Add remote origin (replace YOUR_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_USERNAME/CustomGuiReworked.git

# Set the main branch (if not already set)
git branch -M main

# Push to GitHub
git push -u origin main
```

## Step 4: Update README Links

After creating the repository, update the following files with your actual GitHub username:

### README.md
Replace all instances of `your-username` with your actual GitHub username:
- `https://github.com/your-username/CustomGuiReworked` → `https://github.com/YOUR_USERNAME/CustomGuiReworked`
- `https://github.com/your-repo/CustomGuiReworked` → `https://github.com/YOUR_USERNAME/CustomGuiReworked`

### CONTRIBUTING.md
Update the GitHub links with your username.

## Step 5: Create GitHub Pages (Optional)

If you want to create a project website:

1. Go to your repository on GitHub
2. Click "Settings"
3. Scroll down to "GitHub Pages"
4. Select "Deploy from a branch"
5. Choose "main" branch and "/docs" folder
6. Click "Save"

## Step 6: Set Up Branch Protection (Recommended)

1. Go to repository "Settings"
2. Click "Branches"
3. Click "Add rule"
4. Set up protection for the "main" branch:
   - Require pull request reviews
   - Require status checks to pass
   - Include administrators

## Step 7: Create Release

1. Go to "Releases" in your repository
2. Click "Create a new release"
3. Fill in the details:
   - **Tag version**: `v1.0.0`
   - **Release title**: `CustomGuiReworked v1.0.0`
   - **Description**: Copy from CHANGELOG.md
4. Upload the compiled JAR file
5. Click "Publish release"

## Step 8: Set Up GitHub Actions (Optional)

Create `.github/workflows/build.yml` for automated builds:

```yaml
name: Build and Test

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v2
    
    - name: Set up JDK 17
      uses: actions/setup-java@v2
      with:
        java-version: '17'
        distribution: 'adopt'
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Build with Gradle
      run: ./gradlew build
    
    - name: Upload build artifacts
      uses: actions/upload-artifact@v2
      with:
        name: CustomGuiReworked
        path: build/libs/
```

## Step 9: Update Documentation

After setting up the repository, update these files:

1. **README.md**: Update all GitHub links
2. **API_DOCUMENTATION.md**: Update repository links
3. **plugin.yml**: Ensure version and author information is correct

## Step 10: Community Setup

1. **Create Issues Template**: Go to Settings → General → Features → Issues → Enable issues
2. **Create Pull Request Template**: Add `.github/pull_request_template.md`
3. **Set up Wiki**: Enable wiki in repository settings
4. **Create Discussions**: Enable discussions for community interaction

## Useful Git Commands

```bash
# Check status
git status

# Add specific files
git add filename

# Commit with message
git commit -m "Your commit message"

# Push changes
git push

# Pull latest changes
git pull

# Create new branch
git checkout -b feature/new-feature

# Switch branches
git checkout main

# Merge branch
git merge feature/new-feature

# View commit history
git log --oneline
```

## Repository Structure

Your repository should look like this:

```
CustomGuiReworked/
├── .github/
│   └── workflows/
│       └── build.yml
├── src/
│   └── main/
│       ├── java/
│       └── resources/
├── .gitignore
├── build.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── README.md
├── LICENSE
├── CHANGELOG.md
├── CONTRIBUTING.md
├── API_DOCUMENTATION.md
├── README_API.md
└── GIT_SETUP.md
```

## Next Steps

1. **Share your repository**: Post on Minecraft forums, Discord servers
2. **Create documentation**: Set up wiki pages for detailed guides
3. **Respond to issues**: Help users with problems
4. **Accept contributions**: Review and merge pull requests
5. **Maintain the project**: Regular updates and bug fixes

## Troubleshooting

### Common Issues

1. **Permission denied**: Make sure you have write access to the repository
2. **Branch conflicts**: Use `git pull --rebase` to resolve conflicts
3. **Large files**: Check `.gitignore` to exclude build artifacts
4. **Authentication**: Use GitHub CLI or personal access tokens

### Getting Help

- [GitHub Help](https://help.github.com/)
- [Git Documentation](https://git-scm.com/doc)
- [GitHub Community](https://github.community/)

---

**Congratulations!** Your CustomGuiReworked project is now properly set up on GitHub and ready for the Minecraft community to discover and use.
