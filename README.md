# KoreanStudyHub (KSH)

Welcome to the KoreanStudyHub project! This repository uses **OpenSpec** combined with AI workflows to maintain consistent business rules and automate our development process. 

## 🚀 1. Getting Started with OpenSpec

To work effectively with the project, you need to install the OpenSpec CLI globally on your machine. This tool allows you to validate specifications and manage feature changes.

### Prerequisites
- [Node.js & npm](https://nodejs.org/) installed on your machine.

### Installation
Open your terminal and run the following command:
```bash
npm install -g @fission-ai/openspec@latest
```

Verify the installation by checking the version:
```bash
openspec --version
```

---

## 🛠 2. How Our OpenSpec Workflow Works

Our project stores all business logic, requirements, and system messages in Markdown files. When you ask an AI Assistant (like Gemini IDE / Antigravity, Cursor, etc.) to build a feature, it will automatically read these specifications to ensure the generated code perfectly matches the requirements.

### Directory Structure
- `openspec/specs/`: Contains the official, validated specifications for all 9 domains of the platform (Authentication, Classroom Management, etc.).
- `docs/appendix.md`: Contains all 113 Business Rules (BR), System Messages (MSG), and Non-Functional Requirements (NFR). **Always refer to this file when coding business logic.**
- `.agent/`: Contains the AI agent skills and custom slash commands (e.g., `/opsx-propose`).
- `openspec/changes/`: Where new feature implementations are planned and tracked.

---

## 🤖 3. How to Implement a New Feature using AI

Instead of manually coding from scratch and risking missing business rules, follow this Spec-Driven Development workflow:

### Step 1: Propose the Feature
Open your AI chat window and type:
```
/opsx-propose "implement authentication login and register"
```
The AI will read the `openspec/specs/authentication/spec.md` and generate a `proposal.md`, `design.md`, and `tasks.md` in a new change folder (under `openspec/changes/`).

### Step 2: Review & Execute
Review the generated `design.md` and `tasks.md`. If it looks good, tell the AI to start executing the tasks:
```
/opsx-apply
```
The AI will begin coding the feature, automatically handling the backend (Spring Boot, Controllers, Services) and frontend (Thymeleaf, Bootstrap) according to the architecture.

### Step 3: Archive the Change
Once the feature is completely implemented, tested, and works perfectly, tell the AI to archive it:
```
/opsx-archive
```

---

## 🔍 4. Useful OpenSpec CLI Commands

If you modify the specification files in `openspec/specs/`, you must validate them before committing to GitHub:

- **List all specs and requirement counts:**
  ```bash
  openspec list --specs
  ```

- **Validate all spec files:**
  ```bash
  openspec validate --specs
  ```
  *(Must show 100% passed before you commit!)*

---

## ⚙️ 5. Local Environment Setup

1. **Java Development Kit:** Ensure you have **JDK 26** installed.
2. **Database:** We use MySQL 8.0 running via Docker. To start the database:
   ```bash
   docker-compose up -d
   ```
3. **Run Application:** Start the Spring Boot application using Maven:
   ```bash
   mvn spring-boot:run
   ```

Happy Coding! 🎉
