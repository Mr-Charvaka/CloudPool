#!/usr/bin/env node

/**
 * CloudPool Command Line Interface (CLI)
 * A professional-grade, interactive CLI tool to manage CloudPool microservices natively from the terminal.
 */

const fs = require('fs');
const path = require('path');
const os = require('os');
const { program } = require('commander');
const chalk = require('chalk');
const ora = require('ora');
const inquirer = require('inquirer');
const Table = require('cli-table3');
const WebSocket = require('ws');

const CONFIG_FILE = path.join(os.homedir(), '.cloudpool-cli.json');
const API_BASE = process.env.CLOUDPOOL_API_URL || 'http://localhost:8080';

const BANNER = chalk.cyan.bold(`
   ______ __                 __ ____                __ 
  / ____// /____   __  __   / // __ \\ ____   ____  / / 
 / /    / // __ \\ / / / /  / // /_/ // __ \\ / __ \\/ /  
/ /___ / // /_/ // /_/ /  / // ____// /_/ // /_/ // /   
\\____//_/ \\____/ \\__,_/  /_//_/     \\____/ \\____//_/    
                           AI-Native Backend Platform
`);

// === Config Management Helpers ===

function loadConfig() {
  if (fs.existsSync(CONFIG_FILE)) {
    try {
      return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf8'));
    } catch (e) {
      return {};
    }
  }
  return {};
}

function saveConfig(config) {
  fs.writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2), 'utf8');
}

function ensureAuthenticated(config) {
  if (!config.token) {
    console.error(chalk.red('\n[ERROR] You must log in first. Run "cloudpool auth login".'));
    process.exit(1);
  }
  return config.token;
}

function ensureActiveProject(config) {
  if (!config.projectId) {
    console.error(chalk.red('\n[ERROR] No active project set. Run "cloudpool project set" or "cloudpool project list" to choose one.'));
    process.exit(1);
  }
  return config.projectId;
}

// === Request Helper ===

async function request(method, endpoint, body = null, spinnerMsg = null) {
  const config = loadConfig();
  const url = `${API_BASE}${endpoint}`;
  
  const headers = {
    'Content-Type': 'application/json'
  };
  if (config.token) {
    headers['Authorization'] = `Bearer ${config.token}`;
  }
  if (config.projectId) {
    headers['X-Project-Id'] = config.projectId;
  }

  let spinner;
  if (spinnerMsg) {
    spinner = ora(chalk.cyan(spinnerMsg)).start();
  }

  try {
    const options = {
      method,
      headers
    };
    if (body) {
      options.body = typeof body === 'string' ? body : JSON.stringify(body);
    }

    const res = await fetch(url, options);
    
    if (spinner) {
      if (res.ok) {
        spinner.succeed();
      } else {
        spinner.fail();
      }
    }

    let responseData;
    const contentType = res.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      responseData = await res.json();
    } else {
      responseData = await res.text();
    }

    if (!res.ok) {
      const errorMsg = responseData?.error || responseData?.message || JSON.stringify(responseData) || `HTTP ${res.status}`;
      throw new Error(errorMsg);
    }

    return responseData;
  } catch (err) {
    if (spinner) {
      spinner.fail();
    }
    throw err;
  }
}

// === Table Renderer ===

function renderTable(headers, rows) {
  const table = new Table({
    head: headers.map(h => chalk.yellow.bold(h)),
    style: {
      head: [],
      border: ['gray']
    }
  });
  rows.forEach(row => table.push(row));
  console.log(table.toString());
}

// === Auth Handlers ===

async function handleLogin(email, password) {
  try {
    const answers = await inquirer.prompt([
      { name: 'email', message: 'Email address:', default: email, validate: val => val ? true : 'Email is required' },
      { name: 'password', type: 'password', message: 'Password:', default: password, mask: '*', validate: val => val ? true : 'Password is required' }
    ]);
    const res = await request('POST', '/api/auth/login', { email: answers.email, password: answers.password }, 'Authenticating...');
    const config = loadConfig();
    config.token = res.token;
    config.email = res.email;
    config.name = res.name;
    saveConfig(config);
    console.log(chalk.green.bold(`\n[SUCCESS] Logged in successfully as ${res.name} (${res.email}).`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Login failed: ${err.message}`));
  }
}

async function handleSignup(email, name, password) {
  try {
    const answers = await inquirer.prompt([
      { name: 'email', message: 'Email address:', default: email, validate: val => val ? true : 'Email is required' },
      { name: 'name', message: 'Name:', default: name, validate: val => val ? true : 'Name is required' },
      { name: 'password', type: 'password', message: 'Password:', default: password, mask: '*', validate: val => val ? true : 'Password is required' }
    ]);
    const res = await request('POST', '/api/auth/register', { email: answers.email, name: answers.name, password: answers.password }, 'Registering account...');
    const config = loadConfig();
    config.token = res.token;
    config.email = res.email;
    config.name = res.name;
    saveConfig(config);
    console.log(chalk.green.bold(`\n[SUCCESS] Registered and logged in successfully as ${res.name}.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Registration failed: ${err.message}`));
  }
}

async function handleLogout() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    await request('POST', '/api/auth/logout', null, 'Logging out...');
    config.token = null;
    config.email = null;
    config.name = null;
    saveConfig(config);
    console.log(chalk.green.bold(`\n[SUCCESS] Logged out successfully.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Logout failed: ${err.message}`));
  }
}

function handleStatus() {
  const config = loadConfig();
  console.log(BANNER);
  console.log(chalk.bold('--- CLI STATUS ---'));
  console.log(`API URL:        ${chalk.cyan(API_BASE)}`);
  if (config.token) {
    console.log(`Logged In As:   ${chalk.green(`${config.name} (${config.email})`)}`);
    console.log(`Token Status:   ${chalk.green('Active')}`);
  } else {
    console.log(`Logged In:      ${chalk.red('No')}`);
  }
  if (config.projectId) {
    console.log(`Active Project: ${chalk.yellow(config.projectId)}`);
  } else {
    console.log(`Active Project: ${chalk.red('None set')}`);
  }
}

async function handleUserCreate(email, password) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const answers = await inquirer.prompt([
      { name: 'email', message: 'Tenant User Email:', default: email, validate: val => val ? true : 'Email is required' },
      { name: 'displayName', message: 'Display Name:', default: 'Tenant User' },
      { name: 'password', type: 'password', message: 'Password:', default: password, mask: '*', validate: val => val ? true : 'Password is required' }
    ]);
    
    await request('POST', `/api/v1/projects/${projectId}/auth/signup`, {
      email: answers.email,
      password: answers.password,
      displayName: answers.displayName
    }, 'Creating tenant user...');
    
    console.log(chalk.green.bold(`\n[SUCCESS] Tenant user ${answers.email} created successfully.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] User creation failed: ${err.message}`));
  }
}

async function handleUserList() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const users = await request('GET', `/api/v1/projects/${projectId}/auth/users`, null, 'Loading users...');
    if (!users || users.length === 0) {
      console.log(chalk.yellow('\nNo tenant users found in this project.'));
      return;
    }
    
    renderTable(
      ['ID', 'Email', 'Display Name', 'Created At'],
      users.map(u => [u.id, u.email, u.displayName || '', u.createdAt || ''])
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to load users: ${err.message}`));
  }
}

async function handleUserDelete(userId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!userId) {
      const users = await request('GET', `/api/v1/projects/${projectId}/auth/users`, null, 'Loading users...');
      if (!users || users.length === 0) {
        console.log(chalk.yellow('\nNo users available to delete.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'userId',
          type: 'list',
          message: 'Select user to delete:',
          choices: users.map(u => ({ name: `${u.displayName || 'Unnamed'} (${u.email})`, value: u.id }))
        }
      ]);
      userId = answers.userId;
    }
    
    const confirm = await inquirer.prompt([
      { name: 'ok', type: 'confirm', message: `Are you sure you want to delete user ${userId}?`, default: false }
    ]);
    if (!confirm.ok) return;

    await request('DELETE', `/api/v1/projects/${projectId}/auth/users/${userId}`, null, 'Deleting user...');
    console.log(chalk.green.bold(`\n[SUCCESS] User deleted successfully.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to delete user: ${err.message}`));
  }
}

// === Project Handlers ===

async function handleProjectList() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    
    const projects = await request('GET', '/api/v1/projects', null, 'Loading projects...');
    if (!projects || projects.length === 0) {
      console.log(chalk.yellow('\nNo projects found. Create one with "cloudpool project create".'));
      return;
    }
    
    renderTable(
      ['ID', 'Name', 'Description', 'Active?'],
      projects.map(p => {
        const isActive = p.id === config.projectId ? chalk.green.bold('Active') : '';
        return [p.id, p.name, p.description || '', isActive];
      })
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to list projects: ${err.message}`));
  }
}

async function handleProjectCreate(name, description) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    
    const answers = await inquirer.prompt([
      { name: 'name', message: 'Project Name:', default: name, validate: val => val ? true : 'Project name is required' },
      { name: 'description', message: 'Project Description:', default: description }
    ]);
    
    const res = await request('POST', '/api/v1/projects', { name: answers.name, description: answers.description }, 'Creating project...');
    console.log(chalk.green.bold(`\n[SUCCESS] Project created successfully.`));
    console.log(`Project ID: ${chalk.yellow(res.id)}`);
    
    const setConfirm = await inquirer.prompt([
      { name: 'set', type: 'confirm', message: 'Set this project as your active project?', default: true }
    ]);
    if (setConfirm.set) {
      config.projectId = res.id;
      saveConfig(config);
      console.log(chalk.green(`Active project set to ${res.id}`));
    }
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Project creation failed: ${err.message}`));
  }
}

async function handleProjectDelete(projectId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    
    if (!projectId) {
      const projects = await request('GET', '/api/v1/projects', null, 'Loading projects...');
      if (!projects || projects.length === 0) {
        console.log(chalk.yellow('\nNo projects found to delete.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'projectId',
          type: 'list',
          message: 'Select project to delete:',
          choices: projects.map(p => ({ name: p.name, value: p.id }))
        }
      ]);
      projectId = answers.projectId;
    }
    
    const confirm = await inquirer.prompt([
      { name: 'ok', type: 'confirm', message: `Are you sure you want to delete project ${projectId}? This is permanent!`, default: false }
    ]);
    if (!confirm.ok) return;

    await request('DELETE', `/api/v1/projects/${projectId}`, null, 'Deleting project...');
    console.log(chalk.green.bold(`\n[SUCCESS] Project deleted.`));
    
    if (config.projectId === projectId) {
      config.projectId = null;
      saveConfig(config);
      console.log(chalk.yellow('Cleared active project config.'));
    }
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to delete project: ${err.message}`));
  }
}

async function handleProjectSet(projectId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    
    if (!projectId) {
      const projects = await request('GET', '/api/v1/projects', null, 'Loading projects...');
      if (!projects || projects.length === 0) {
        console.log(chalk.yellow('\nNo projects found. Create one first.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'projectId',
          type: 'list',
          message: 'Select project to set active:',
          choices: projects.map(p => ({ name: p.name, value: p.id }))
        }
      ]);
      projectId = answers.projectId;
    }
    
    config.projectId = projectId;
    saveConfig(config);
    console.log(chalk.green.bold(`\n[SUCCESS] Active project set to ${projectId}.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to set project: ${err.message}`));
  }
}

async function handleSecretSet(key, value) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const answers = await inquirer.prompt([
      { name: 'key', message: 'Secret Key:', default: key, validate: val => val ? true : 'Key is required' },
      { name: 'value', message: 'Secret Value:', default: value, validate: val => val ? true : 'Value is required' }
    ]);
    
    await request('POST', `/api/v1/projects/${projectId}/secrets`, { key: answers.key, value: answers.value }, 'Setting secret...');
    console.log(chalk.green.bold(`\n[SUCCESS] Secret '${answers.key}' saved.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to set secret: ${err.message}`));
  }
}

async function handleSecretList() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const secrets = await request('GET', `/api/v1/projects/${projectId}/secrets`, null, 'Loading secrets...');
    if (!secrets || secrets.length === 0) {
      console.log(chalk.yellow('\nNo secrets found in this project.'));
      return;
    }
    
    renderTable(
      ['ID', 'Secret Key', 'Created At'],
      secrets.map(s => [s.id, s.secretKey, s.createdAt || ''])
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to load secrets: ${err.message}`));
  }
}

async function handleSecretRm(secretId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!secretId) {
      const secrets = await request('GET', `/api/v1/projects/${projectId}/secrets`, null, 'Loading secrets...');
      if (!secrets || secrets.length === 0) {
        console.log(chalk.yellow('\nNo secrets found to delete.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'secretId',
          type: 'list',
          message: 'Select secret to remove:',
          choices: secrets.map(s => ({ name: s.secretKey, value: s.id }))
        }
      ]);
      secretId = answers.secretId;
    }
    
    const confirm = await inquirer.prompt([
      { name: 'ok', type: 'confirm', message: `Are you sure you want to delete secret ${secretId}?`, default: false }
    ]);
    if (!confirm.ok) return;

    await request('DELETE', `/api/v1/projects/secrets/${secretId}`, null, 'Deleting secret...');
    console.log(chalk.green.bold(`\n[SUCCESS] Secret deleted.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to delete secret: ${err.message}`));
  }
}

// === DB Handlers ===

async function handleTableCreate(name, displayName, description) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const answers = await inquirer.prompt([
      { name: 'name', message: 'Table Name (alphanumeric & underscores only):', default: name, validate: val => /^[a-zA-Z][a-zA-Z0-9_]*$/.test(val) ? true : 'Invalid identifier' },
      { name: 'displayName', message: 'Display Name:', default: displayName },
      { name: 'description', message: 'Description:', default: description }
    ]);
    
    console.log(chalk.cyan.bold('\n--- Table Schema Field Setup ---'));
    console.log(chalk.dim('Each table auto-generates a primary key field "id" (VARCHAR).'));
    
    const fields = [];
    let addMore = true;
    while (addMore) {
      const fieldAnswers = await inquirer.prompt([
        { name: 'fieldName', message: 'Field name:', validate: val => /^[a-zA-Z][a-zA-Z0-9_]*$/.test(val) ? true : 'Invalid identifier' },
        { name: 'fieldType', type: 'list', message: 'Field type:', choices: ['VARCHAR', 'INTEGER', 'BOOLEAN', 'DOUBLE', 'TEXT'] },
        { name: 'isRequired', type: 'confirm', message: 'Is this field required?', default: false },
        { name: 'more', type: 'confirm', message: 'Do you want to add another field?', default: true }
      ]);
      fields.push({
        fieldName: fieldAnswers.fieldName,
        fieldType: fieldAnswers.fieldType,
        isRequired: fieldAnswers.isRequired
      });
      addMore = fieldAnswers.more;
    }
    
    await request('POST', '/api/v1/db/tables', {
      projectId,
      name: answers.name,
      displayName: answers.displayName,
      description: answers.description,
      fields
    }, 'Provisioning database table schema...');
    
    console.log(chalk.green.bold(`\n[SUCCESS] Table '${answers.name}' provisioned successfully.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Table creation failed: ${err.message}`));
  }
}

async function handleTableList() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const tables = await request('GET', `/api/v1/db/tables?projectId=${projectId}`, null, 'Loading tables...');
    if (!tables || tables.length === 0) {
      console.log(chalk.yellow('\nNo database tables found in this project. Create one with "cloudpool db table:create".'));
      return;
    }
    
    renderTable(
      ['ID', 'Physical Name', 'Display Name', 'Created At'],
      tables.map(t => [t.id, t.name, t.displayName || '', t.createdAt || ''])
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to list tables: ${err.message}`));
  }
}

async function handleTableShow(tableId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!tableId) {
      const tables = await request('GET', `/api/v1/db/tables?projectId=${projectId}`, null, 'Loading tables...');
      if (!tables || tables.length === 0) {
        console.log(chalk.yellow('\nNo tables found.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'tableId',
          type: 'list',
          message: 'Select table to view:',
          choices: tables.map(t => ({ name: `${t.displayName} (${t.name})`, value: t.id }))
        }
      ]);
      tableId = answers.tableId;
    }
    
    const fields = await request('GET', `/api/v1/db/tables/${tableId}/fields`, null, 'Loading fields...');
    
    console.log(chalk.cyan.bold(`\nTable Schema Fields:`));
    renderTable(
      ['Field Name', 'Field Type', 'Required?'],
      fields.map(f => [f.fieldName, f.fieldType, f.isRequired ? chalk.red('Yes') : 'No'])
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to load table schema: ${err.message}`));
  }
}

async function handleTableDelete(tableId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!tableId) {
      const tables = await request('GET', `/api/v1/db/tables?projectId=${projectId}`, null, 'Loading tables...');
      if (!tables || tables.length === 0) {
        console.log(chalk.yellow('\nNo tables found to delete.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'tableId',
          type: 'list',
          message: 'Select table to delete:',
          choices: tables.map(t => ({ name: `${t.displayName} (${t.name})`, value: t.id }))
        }
      ]);
      tableId = answers.tableId;
    }
    
    const confirm = await inquirer.prompt([
      { name: 'ok', type: 'confirm', message: `Are you sure you want to DROP table ${tableId}? THIS DROPS PHYSICAL SQL DATA!`, default: false }
    ]);
    if (!confirm.ok) return;

    await request('DELETE', `/api/v1/db/tables/${tableId}`, null, 'Deleting table...');
    console.log(chalk.green.bold(`\n[SUCCESS] Table dropped.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to drop table: ${err.message}`));
  }
}

async function handleRecordInsert(tableId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!tableId) {
      const tables = await request('GET', `/api/v1/db/tables?projectId=${projectId}`, null, 'Loading tables...');
      if (!tables || tables.length === 0) {
        console.log(chalk.yellow('\nNo tables found to insert into.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'tableId',
          type: 'list',
          message: 'Select table:',
          choices: tables.map(t => ({ name: `${t.displayName} (${t.name})`, value: t.id }))
        }
      ]);
      tableId = answers.tableId;
    }
    
    const fields = await request('GET', `/api/v1/db/tables/${tableId}/fields`, null, 'Loading schema fields...');
    
    console.log(chalk.cyan(`\nEnter row field values:`));
    const prompts = [];
    fields.forEach(f => {
      if (f.fieldName === 'id') return;
      
      const p = {
        name: f.fieldName,
        message: `${f.fieldName} (${f.fieldType}${f.isRequired ? ' - REQUIRED' : ''}):`
      };
      
      if (f.fieldType === 'BOOLEAN') {
        p.type = 'list';
        p.choices = ['true', 'false'];
      }
      
      if (f.isRequired) {
        p.validate = val => val !== '' ? true : `${f.fieldName} is required`;
      }
      
      prompts.push(p);
    });
    
    const recordData = await inquirer.prompt(prompts);
    const res = await request('POST', `/api/v1/db/tables/${tableId}/records`, recordData, 'Inserting record...');
    console.log(chalk.green.bold(`\n[SUCCESS] Record inserted successfully:`));
    console.log(JSON.stringify(res, null, 2));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to insert record: ${err.message}`));
  }
}

async function handleRecordQuery(tableId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!tableId) {
      const tables = await request('GET', `/api/v1/db/tables?projectId=${projectId}`, null, 'Loading tables...');
      if (!tables || tables.length === 0) {
        console.log(chalk.yellow('\nNo tables found to query.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'tableId',
          type: 'list',
          message: 'Select table to query:',
          choices: tables.map(t => ({ name: `${t.displayName} (${t.name})`, value: t.id }))
        }
      ]);
      tableId = answers.tableId;
    }
    
    const records = await request('GET', `/api/v1/db/tables/${tableId}/records`, null, 'Querying records...');
    if (!records || records.length === 0) {
      console.log(chalk.yellow('\nNo records found in this table.'));
      return;
    }
    
    const headers = new Set();
    records.forEach(r => Object.keys(r).forEach(k => headers.add(k)));
    const headersArr = Array.from(headers);
    
    const rows = records.map(r => headersArr.map(h => String(r[h] !== undefined && r[h] !== null ? r[h] : '')));
    renderTable(headersArr, rows);
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to query records: ${err.message}`));
  }
}

async function handleRecordDelete(tableId, recordId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!tableId) {
      const tables = await request('GET', `/api/v1/db/tables?projectId=${projectId}`, null, 'Loading tables...');
      if (!tables || tables.length === 0) {
        console.log(chalk.yellow('\nNo tables found.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'tableId',
          type: 'list',
          message: 'Select table:',
          choices: tables.map(t => ({ name: `${t.displayName} (${t.name})`, value: t.id }))
        }
      ]);
      tableId = answers.tableId;
    }
    
    if (!recordId) {
      const records = await request('GET', `/api/v1/db/tables/${tableId}/records`, null, 'Loading records...');
      if (!records || records.length === 0) {
        console.log(chalk.yellow('\nNo records found to delete.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'recordId',
          type: 'list',
          message: 'Select record to delete:',
          choices: records.map(r => ({ name: JSON.stringify(r), value: String(r.id || r.ID || '') }))
        }
      ]);
      recordId = answers.recordId;
    }
    
    const confirm = await inquirer.prompt([
      { name: 'ok', type: 'confirm', message: `Are you sure you want to delete record ${recordId}?`, default: false }
    ]);
    if (!confirm.ok) return;

    await request('DELETE', `/api/v1/db/tables/${tableId}/records/${recordId}`, null, 'Deleting record...');
    console.log(chalk.green.bold(`\n[SUCCESS] Record deleted successfully.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to delete record: ${err.message}`));
  }
}

async function handleConnList() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const conns = await request('GET', `/api/v1/projects/${projectId}/connections`, null, 'Loading connections...');
    if (!conns || conns.length === 0) {
      console.log(chalk.yellow('\nNo external database connections configured.'));
      return;
    }
    
    renderTable(
      ['ID', 'DB Type', 'Host', 'Port', 'Database Name', 'Username', 'Active?'],
      conns.map(c => [c.id, c.dbType, c.host, c.port, c.databaseName, c.username || '', c.active ? chalk.green.bold('Yes') : 'No'])
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to list connections: ${err.message}`));
  }
}

async function handleConnCreate() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const answers = await inquirer.prompt([
      { name: 'dbType', type: 'list', message: 'Database Type:', choices: ['POSTGRESQL', 'H2'] },
      { name: 'host', message: 'Host name/IP:', default: 'localhost' },
      { name: 'port', type: 'number', message: 'Port number:', default: 5432 },
      { name: 'databaseName', message: 'Database Name:', default: 'cloudpooldb' },
      { name: 'username', message: 'Username:', default: 'postgres' },
      { name: 'password', type: 'password', message: 'Password:', mask: '*' },
      { name: 'active', type: 'confirm', message: 'Set as active connection?', default: true }
    ]);
    
    await request('POST', `/api/v1/projects/${projectId}/connections`, answers, 'Saving database connection...');
    console.log(chalk.green.bold(`\n[SUCCESS] Connection saved successfully.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to save connection: ${err.message}`));
  }
}

async function handleConnDelete(connectionId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!connectionId) {
      const conns = await request('GET', `/api/v1/projects/${projectId}/connections`, null, 'Loading connections...');
      if (!conns || conns.length === 0) {
        console.log(chalk.yellow('\nNo connections found to delete.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'connectionId',
          type: 'list',
          message: 'Select connection to remove:',
          choices: conns.map(c => ({ name: `${c.dbType} (${c.host}:${c.port}/${c.databaseName})`, value: c.id }))
        }
      ]);
      connectionId = answers.connectionId;
    }
    
    const confirm = await inquirer.prompt([
      { name: 'ok', type: 'confirm', message: `Are you sure you want to delete connection ${connectionId}?`, default: false }
    ]);
    if (!confirm.ok) return;

    await request('DELETE', `/api/v1/projects/connections/${connectionId}`, null, 'Removing connection...');
    console.log(chalk.green.bold(`\n[SUCCESS] Database connection deleted.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to delete connection: ${err.message}`));
  }
}

async function handleConnTest() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const answers = await inquirer.prompt([
      { name: 'dbType', type: 'list', message: 'Database Type:', choices: ['POSTGRESQL', 'H2'] },
      { name: 'host', message: 'Host:', default: 'localhost' },
      { name: 'port', type: 'number', message: 'Port:', default: 5432 },
      { name: 'databaseName', message: 'Database Name:', default: 'cloudpooldb' },
      { name: 'username', message: 'Username:', default: 'postgres' },
      { name: 'password', type: 'password', message: 'Password:', mask: '*' }
    ]);
    
    const res = await request('POST', `/api/v1/projects/${projectId}/connections/test`, answers, 'Testing connection credentials...');
    if (res.success) {
      console.log(chalk.green.bold(`\n[SUCCESS] Connection tested successfully! Credentials are valid.`));
    } else {
      console.log(chalk.red.bold(`\n[FAILURE] Connection test failed: ${res.error || 'Unknown error'}`));
    }
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Connection test failed: ${err.message}`));
  }
}

async function handleSnapshotCreate(name) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const answers = await inquirer.prompt([
      { name: 'name', message: 'Snapshot label/name:', default: name, validate: val => val ? true : 'Snapshot name is required' }
    ]);
    
    await request('POST', `/api/v1/projects/${projectId}/snapshots`, { name: answers.name }, 'Creating snapshot...');
    console.log(chalk.green.bold(`\n[SUCCESS] Snapshot '${answers.name}' created.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to create snapshot: ${err.message}`));
  }
}

async function handleSnapshotList() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const snapshots = await request('GET', `/api/v1/projects/${projectId}/snapshots`, null, 'Loading snapshots...');
    if (!snapshots || snapshots.length === 0) {
      console.log(chalk.yellow('\nNo configuration snapshots found.'));
      return;
    }
    
    renderTable(
      ['ID', 'Label', 'Created At'],
      snapshots.map(s => [s.id, s.name, s.createdAt || ''])
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to list snapshots: ${err.message}`));
  }
}

async function handleSnapshotRestore(snapshotId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!snapshotId) {
      const snapshots = await request('GET', `/api/v1/projects/${projectId}/snapshots`, null, 'Loading snapshots...');
      if (!snapshots || snapshots.length === 0) {
        console.log(chalk.yellow('\nNo snapshots available to restore.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'snapshotId',
          type: 'list',
          message: 'Select snapshot to restore:',
          choices: snapshots.map(s => ({ name: s.name, value: s.id }))
        }
      ]);
      snapshotId = answers.snapshotId;
    }
    
    const confirm = await inquirer.prompt([
      { name: 'ok', type: 'confirm', message: `Are you sure you want to restore snapshot ${snapshotId}? This will overwrite active configs!`, default: false }
    ]);
    if (!confirm.ok) return;

    await request('POST', `/api/v1/projects/${projectId}/snapshots/${snapshotId}/restore`, {}, 'Restoring snapshot...');
    console.log(chalk.green.bold(`\n[SUCCESS] Project configuration restored successfully.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Snapshot restoration failed: ${err.message}`));
  }
}

// === KV Handlers ===

async function handleKvList() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const keys = await request('GET', `/api/v1/projects/${projectId}/kv`, null, 'Loading KV pairs...');
    if (!keys || keys.length === 0) {
      console.log(chalk.yellow('\nNo KV pairs found.'));
      return;
    }
    
    renderTable(
      ['Key', 'Value', 'TTL (sec)'],
      keys.map(k => [k.keyName, k.value || '', k.ttlSeconds !== null ? String(k.ttlSeconds) : 'Indefinite'])
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to list KV keys: ${err.message}`));
  }
}

async function handleKvGet(key) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!key) {
      const answers = await inquirer.prompt([
        { name: 'key', message: 'Key name:', validate: val => val ? true : 'Key is required' }
      ]);
      key = answers.key;
    }
    
    const res = await request('GET', `/api/v1/projects/${projectId}/kv/${key}`, null, 'Loading key...');
    console.log(`\nValue: ${chalk.green(res.value)}`);
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to fetch key: ${err.message}`));
  }
}

async function handleKvSet(key, value, ttlSeconds) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const answers = await inquirer.prompt([
      { name: 'key', message: 'Key:', default: key, validate: val => val ? true : 'Key is required' },
      { name: 'value', message: 'Value:', default: value, validate: val => val ? true : 'Value is required' },
      { name: 'ttl', type: 'number', message: 'TTL (Seconds, or enter 0 for Indefinite):', default: ttlSeconds ? parseInt(ttlSeconds) : 0 }
    ]);
    
    const payload = { value: answers.value };
    if (answers.ttl > 0) {
      payload.ttlSeconds = answers.ttl;
    }
    
    await request('PUT', `/api/v1/projects/${projectId}/kv/${answers.key}`, payload, 'Setting KV value...');
    console.log(chalk.green.bold(`\n[SUCCESS] Key '${answers.key}' saved.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to set key: ${err.message}`));
  }
}

async function handleKvRm(key) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!key) {
      const answers = await inquirer.prompt([
        { name: 'key', message: 'Key to remove:', validate: val => val ? true : 'Key is required' }
      ]);
      key = answers.key;
    }
    
    const confirm = await inquirer.prompt([
      { name: 'ok', type: 'confirm', message: `Are you sure you want to delete KV key '${key}'?`, default: false }
    ]);
    if (!confirm.ok) return;

    await request('DELETE', `/api/v1/projects/${projectId}/kv/${key}`, null, 'Removing key...');
    console.log(chalk.green.bold(`\n[SUCCESS] Key deleted.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to delete key: ${err.message}`));
  }
}

// === Vector Handlers ===

async function handleVectorSearch(query) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    
    if (!query) {
      const answers = await inquirer.prompt([
        { name: 'query', message: 'Search term:', validate: val => val ? true : 'Query is required' }
      ]);
      query = answers.query;
    }
    
    const results = await request('GET', `/api/vector/search?q=${encodeURIComponent(query)}`, null, 'Searching Vector DB...');
    if (!results || results.length === 0) {
      console.log(chalk.yellow('\nNo matching vectors found.'));
      return;
    }
    
    console.log(chalk.cyan.bold(`\nSearch Results:`));
    console.log(JSON.stringify(results, null, 2));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Vector search failed: ${err.message}`));
  }
}

// === Cron Handlers ===

async function handleCronList() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const jobs = await request('GET', `/api/v1/projects/${projectId}/cron`, null, 'Loading scheduled tasks...');
    if (!jobs || jobs.length === 0) {
      console.log(chalk.yellow('\nNo scheduled tasks found.'));
      return;
    }
    
    renderTable(
      ['ID', 'Name', 'Expression', 'Target URL', 'Method', 'Active?'],
      jobs.map(j => [j.id, j.name, j.cronExpression, j.targetUrl, j.httpMethod || 'POST', j.isActive ? chalk.green.bold('Yes') : 'No'])
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to list cron jobs: ${err.message}`));
  }
}

async function handleCronAdd(name, cronExpression, targetUrl, options) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const answers = await inquirer.prompt([
      { name: 'name', message: 'Job Name:', default: name, validate: val => val ? true : 'Name is required' },
      { name: 'cronExpression', message: 'Cron Expression (e.g. "0 0 * * * *"):', default: cronExpression, validate: val => val ? true : 'Cron expression is required' },
      { name: 'targetUrl', message: 'Target URL:', default: targetUrl, validate: val => val ? true : 'Target URL is required' }
    ]);
    
    const payload = {
      name: answers.name,
      cronExpression: answers.cronExpression,
      targetUrl: answers.targetUrl,
      httpMethod: options.method || 'POST',
      payload: options.payload || null,
      headers: options.headers || null,
      isActive: true
    };
    
    await request('POST', `/api/v1/projects/${projectId}/cron`, payload, 'Scheduling cron task...');
    console.log(chalk.green.bold(`\n[SUCCESS] Cron job scheduled successfully.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to add cron job: ${err.message}`));
  }
}

async function handleCronRm(jobId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!jobId) {
      const jobs = await request('GET', `/api/v1/projects/${projectId}/cron`, null, 'Loading cron jobs...');
      if (!jobs || jobs.length === 0) {
        console.log(chalk.yellow('\nNo cron jobs found to delete.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'jobId',
          type: 'list',
          message: 'Select job to remove:',
          choices: jobs.map(j => ({ name: `${j.name} (${j.cronExpression})`, value: j.id }))
        }
      ]);
      jobId = answers.jobId;
    }
    
    const confirm = await inquirer.prompt([
      { name: 'ok', type: 'confirm', message: `Are you sure you want to delete job ${jobId}?`, default: false }
    ]);
    if (!confirm.ok) return;

    await request('DELETE', `/api/v1/projects/${projectId}/cron/${jobId}`, null, 'Removing cron job...');
    console.log(chalk.green.bold(`\n[SUCCESS] Cron job deleted.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to delete cron job: ${err.message}`));
  }
}

async function handleCronHistory(jobId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!jobId) {
      const jobs = await request('GET', `/api/v1/projects/${projectId}/cron`, null, 'Loading jobs...');
      if (!jobs || jobs.length === 0) {
        console.log(chalk.yellow('\nNo cron jobs found.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'jobId',
          type: 'list',
          message: 'Select cron job:',
          choices: jobs.map(j => ({ name: j.name, value: j.id }))
        }
      ]);
      jobId = answers.jobId;
    }
    
    const executions = await request('GET', `/api/v1/projects/${projectId}/cron/${jobId}/executions`, null, 'Loading execution history...');
    if (!executions || executions.length === 0) {
      console.log(chalk.yellow('\nNo execution history found for this job.'));
      return;
    }
    
    renderTable(
      ['ID', 'Executed At', 'Status Code', 'Response Body', 'Success?'],
      executions.map(e => [
        e.id,
        e.executedAt || '',
        e.statusCode !== null ? String(e.statusCode) : 'N/A',
        e.responseBody || '',
        e.success ? chalk.green.bold('SUCCESS') : chalk.red.bold('FAIL')
      ])
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to load execution history: ${err.message}`));
  }
}

// === WAF Handlers ===

async function handleWafList() {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const rules = await request('GET', `/api/v1/projects/${projectId}/waf`, null, 'Loading firewall rules...');
    if (!rules || rules.length === 0) {
      console.log(chalk.yellow('\nNo active WAF rules found.'));
      return;
    }
    
    renderTable(
      ['ID', 'Rule Type', 'Pattern', 'Action'],
      rules.map(r => [r.id, r.ruleType, r.pattern, r.action])
    );
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to list WAF rules: ${err.message}`));
  }
}

async function handleWafBlock(ip, options) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    const answers = await inquirer.prompt([
      { name: 'pattern', message: 'Pattern (IP address, path pattern, or header value):', default: ip, validate: val => val ? true : 'Pattern is required' },
      { name: 'type', type: 'list', message: 'Rule Type:', choices: ['IP_BLOCK', 'RATE_LIMIT', 'SQLI_BLOCK'], default: options.type },
      { name: 'action', type: 'list', message: 'Action:', choices: ['BLOCK', 'ALLOW'], default: options.action }
    ]);
    
    await request('POST', `/api/v1/projects/${projectId}/waf`, {
      ruleType: answers.type,
      pattern: answers.pattern,
      action: answers.action
    }, 'Adding firewall rule...');
    console.log(chalk.green.bold(`\n[SUCCESS] WAF rule added.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to add WAF rule: ${err.message}`));
  }
}

async function handleWafRm(ruleId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    const projectId = ensureActiveProject(config);
    
    if (!ruleId) {
      const rules = await request('GET', `/api/v1/projects/${projectId}/waf`, null, 'Loading WAF rules...');
      if (!rules || rules.length === 0) {
        console.log(chalk.yellow('\nNo rules found to delete.'));
        return;
      }
      const answers = await inquirer.prompt([
        {
          name: 'ruleId',
          type: 'list',
          message: 'Select WAF rule to delete:',
          choices: rules.map(r => ({ name: `${r.ruleType} (${r.pattern})`, value: r.id }))
        }
      ]);
      ruleId = answers.ruleId;
    }
    
    const confirm = await inquirer.prompt([
      { name: 'ok', type: 'confirm', message: `Are you sure you want to delete WAF rule ${ruleId}?`, default: false }
    ]);
    if (!confirm.ok) return;

    await request('DELETE', `/api/v1/projects/${projectId}/waf/${ruleId}`, null, 'Removing firewall rule...');
    console.log(chalk.green.bold(`\n[SUCCESS] WAF rule deleted.`));
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Failed to delete WAF rule: ${err.message}`));
  }
}

// === Tunnel Handlers ===

async function handleTunnelStart(localPort, tunnelId) {
  try {
    const config = loadConfig();
    ensureAuthenticated(config);
    
    const answers = await inquirer.prompt([
      { name: 'localPort', type: 'number', message: 'Local port to forward:', default: localPort ? parseInt(localPort) : 3000 },
      { name: 'tunnelId', message: 'CloudTunnel ID:', default: tunnelId || Math.random().toString(36).substring(2, 8) }
    ]);
    
    const port = answers.localPort;
    const tid = answers.tunnelId;
    
    const wsUrl = API_BASE.replace(/^http/, 'ws') + '/ws/tunnel';
    console.log(chalk.cyan(`\nConnecting tunnel '${tid}' to CloudPool at ${wsUrl}...`));
    
    const ws = new WebSocket(wsUrl, {
      headers: {
        'Authorization': `Bearer ${config.token}`
      }
    });
    
    const spinner = ora('Establishing WebSocket connection...').start();
    
    ws.on('open', () => {
      spinner.succeed(chalk.green('WebSocket connection established.'));
      
      ws.send(JSON.stringify({
        action: 'register',
        tunnelId: tid
      }));
      console.log(chalk.green.bold(`\n[TUNNEL ONLINE] Proxying requests from ${chalk.yellow(`${API_BASE}/tunnels/${tid}/**`)} to ${chalk.yellow(`http://localhost:${port}/**`)}\n`));
    });
    
    ws.on('message', async (data) => {
      try {
        const payload = JSON.parse(data.toString());
        if (payload.action === 'request') {
          const { requestId, method, uri, body, headers } = payload;
          console.log(chalk.dim(`[HTTP] Forwarding ${method} ${uri}`));
          
          const localUrl = `http://localhost:${port}${uri}`;
          
          const localOpts = {
            method,
            headers: {}
          };
          
          if (headers) {
            headers.split('\n').forEach(line => {
              const parts = line.split(': ');
              if (parts.length === 2) {
                localOpts.headers[parts[0]] = parts[1];
              }
            });
          }
          
          if (['POST', 'PUT', 'PATCH'].includes(method) && body) {
            localOpts.body = body;
          }
          
          try {
            const localRes = await fetch(localUrl, localOpts);
            const resBody = await localRes.text();
            
            ws.send(JSON.stringify({
              action: 'response',
              requestId,
              statusCode: localRes.status,
              body: resBody
            }));
            
            console.log(chalk.green(`[HTTP] 200 Response sent back for request ${requestId}`));
          } catch (fetchErr) {
            console.error(chalk.red(`[HTTP] Error forwarding request to http://localhost:${port}: ${fetchErr.message}`));
            ws.send(JSON.stringify({
              action: 'response',
              requestId,
              statusCode: 502,
              body: `Bad Gateway: Local server error. ${fetchErr.message}`
            }));
          }
        }
      } catch (parseErr) {
        console.error(chalk.red(`Error processing tunnel command: ${parseErr.message}`));
      }
    });
    
    ws.on('close', (code, reason) => {
      console.log(chalk.red(`\n[TUNNEL OFFLINE] Connection closed by server. Code: ${code}, Reason: ${reason}`));
      process.exit(0);
    });
    
    ws.on('error', (err) => {
      spinner.fail('Tunnel WebSocket error.');
      console.error(chalk.red(`\n[ERROR] ${err.message}`));
      process.exit(1);
    });
    
    process.on('SIGINT', () => {
      console.log(chalk.yellow('\nClosing tunnel connection...'));
      ws.close();
      process.exit(0);
    });
    
  } catch (err) {
    console.error(chalk.red(`\n[ERROR] Tunnel startup failed: ${err.message}`));
  }
}

// === Command Definitions ===

program
  .name('cloudpool')
  .version('0.2.0')
  .description('The official CloudPool command-line interface');

// ── Auth Group ──
const auth = program.command('auth').description('IAM & Authentication management');
auth.command('login [email] [password]').description('Log in to your developer account').action(handleLogin);
auth.command('signup [email] [name] [password]').description('Sign up for a developer account').action(handleSignup);
auth.command('logout').description('Log out and clear active credentials').action(handleLogout);
auth.command('status').description('Show current login status and project config').action(handleStatus);
auth.command('user:create [email] [password]').description('Create a tenant/user in the active project').action(handleUserCreate);
auth.command('user:list').description('List tenant/users in the active project').action(handleUserList);
auth.command('user:delete [userId]').description('Delete a tenant/user').action(handleUserDelete);

// ── Project Group ──
const project = program.command('project').description('Project configuration & Secrets Vault');
project.command('list').description('List all projects').action(handleProjectList);
project.command('create [name] [description]').description('Create a new project').action(handleProjectCreate);
project.command('delete [projectId]').description('Delete a project').action(handleProjectDelete);
project.command('set [projectId]').description('Set the active project').action(handleProjectSet);
project.command('secret:set [key] [value]').description('Set a project secret in the vault').action(handleSecretSet);
project.command('secret:list').description('List all project secrets').action(handleSecretList);
project.command('secret:rm [secretId]').description('Delete a secret from the vault').action(handleSecretRm);

// ── DB Group ──
const db = program.command('db').description('Database provisioning, records, and connections');
db.command('table:create [name] [displayName] [description]').description('Provision a new database table schema').action(handleTableCreate);
db.command('table:list').description('List database tables').action(handleTableList);
db.command('table:show [tableId]').description('Show fields of a table').action(handleTableShow);
db.command('table:delete [tableId]').description('Delete a database table').action(handleTableDelete);
db.command('record:insert [tableId]').description('Insert a new record into a table').action(handleRecordInsert);
db.command('record:query [tableId]').description('Query all records from a table').action(handleRecordQuery);
db.command('record:delete [tableId] [recordId]').description('Delete a record from a table').action(handleRecordDelete);
db.command('conn:list').description('List project database connections').action(handleConnList);
db.command('conn:create').description('Create an external database connection').action(handleConnCreate);
db.command('conn:delete [connectionId]').description('Delete a database connection').action(handleConnDelete);
db.command('conn:test').description('Test external database connection credentials').action(handleConnTest);
db.command('snapshot:create [name]').description('Create a versioned project snapshot').action(handleSnapshotCreate);
db.command('snapshot:list').description('List project snapshots').action(handleSnapshotList);
db.command('snapshot:restore [snapshotId]').description('Restore project config to a snapshot').action(handleSnapshotRestore);

// ── KV Group ──
const kv = program.command('kv').description('Key-Value Store management');
kv.command('list').description('List all Key-Value pairs').action(handleKvList);
kv.command('get [key]').description('Get value for a key').action(handleKvGet);
kv.command('set [key] [value] [ttlSeconds]').description('Set a Key-Value pair').action(handleKvSet);
kv.command('rm [key]').description('Remove a Key-Value pair').action(handleKvRm);

// ── Vector Group ──
const vector = program.command('vector').description('Vector DB search');
vector.command('search [query]').description('Search for semantic matches across vector collections').action(handleVectorSearch);

// ── Cron Group ──
const cron = program.command('cron').description('Schedule and monitor Webhook Tasks');
cron.command('list').description('List all active cron tasks').action(handleCronList);
cron.command('add [name] [cronExpression] [targetUrl]')
  .description('Schedule a new webhook task')
  .option('-m, --method <method>', 'HTTP method', 'POST')
  .option('-p, --payload <payload>', 'HTTP payload (JSON string)')
  .option('-h, --headers <headers>', 'HTTP headers (JSON string)')
  .action(handleCronAdd);
cron.command('rm [jobId]').description('Remove a cron job').action(handleCronRm);
cron.command('history [jobId]').description('Get execution logs for a cron job').action(handleCronHistory);

// ── WAF Group ──
const waf = program.command('waf').description('Web Application Firewall rules');
waf.command('list').description('List all WAF rules').action(handleWafList);
waf.command('block [ip]')
  .description('Add WAF rule to block IP')
  .option('-t, --type <type>', 'Rule Type (IP_BLOCK, RATE_LIMIT, SQLI_BLOCK)', 'IP_BLOCK')
  .option('-a, --action <action>', 'Action (BLOCK, ALLOW)', 'BLOCK')
  .action(handleWafBlock);
waf.command('rm [ruleId]').description('Delete a WAF rule').action(handleWafRm);

// ── Tunnel Group ──
const tunnel = program.command('tunnel').description('Cloud Tunnel reverse proxy');
tunnel.command('start [localPort] [tunnelId]').description('Connect local port to CloudPool reverse proxy tunnel').action(handleTunnelStart);

// Handle unknown commands
program.on('command:*', () => {
  console.error(chalk.red('\n[ERROR] Invalid command: %s\nSee --help for a list of available commands.'), program.args.join(' '));
  process.exit(1);
});

// Run
if (process.argv.length === 2) {
  console.log(BANNER);
  program.help();
} else {
  program.parse(process.argv);
}
