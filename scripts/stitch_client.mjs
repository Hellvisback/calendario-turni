#!/usr/bin/env node
import https from 'https';

const API_KEY = process.env.STITCH_API_KEY || 'AQ.Ab8RN6IF79LenE0Ogh1qiqlnxC3PZbdBReiPw0lAyp-u85po7Q';
const ENDPOINT = 'https://stitch.googleapis.com/mcp';

/**
 * Call a tool on the Stitch MCP server
 */
export async function callStitchTool(toolName, args = {}) {
  const payload = JSON.stringify({
    jsonrpc: '2.0',
    id: Date.now(),
    method: 'tools/call',
    params: {
      name: toolName,
      arguments: args,
    },
  });

  return new Promise((resolve, reject) => {
    const req = https.request(
      ENDPOINT,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Goog-Api-Key': API_KEY,
          'Content-Length': Buffer.byteLength(payload),
        },
      },
      (res) => {
        let data = '';
        res.on('data', (chunk) => (data += chunk));
        res.on('end', () => {
          try {
            const parsed = JSON.parse(data);
            if (parsed.error) {
              reject(new Error(`MCP Error: ${JSON.stringify(parsed.error)}`));
            } else {
              const textContent = parsed.result?.content?.[0]?.text;
              if (textContent) {
                try {
                  resolve(JSON.parse(textContent));
                } catch {
                  resolve(textContent);
                }
              } else {
                resolve(parsed.result);
              }
            }
          } catch (err) {
            reject(new Error(`Failed to parse response: ${err.message}\nRaw: ${data}`));
          }
        });
      }
    );

    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

// CLI Execution
if (process.argv[1]?.endsWith('stitch_client.mjs')) {
  const command = process.argv[2] || 'help';
  const args = process.argv.slice(3);

  async function run() {
    switch (command) {
      case 'list-projects': {
        const res = await callStitchTool('list_projects', {});
        console.log(JSON.stringify(res, null, 2));
        break;
      }
      case 'get-project': {
        if (!args[0]) {
          console.error('Usage: node stitch_client.mjs get-project <projectName>');
          process.exit(1);
        }
        const res = await callStitchTool('get_project', { name: args[0] });
        console.log(JSON.stringify(res, null, 2));
        break;
      }
      case 'create-project': {
        const title = args[0] || 'Calediario Turni';
        const res = await callStitchTool('create_project', { title });
        console.log(JSON.stringify(res, null, 2));
        break;
      }
      case 'list-screens': {
        if (!args[0]) {
          console.error('Usage: node stitch_client.mjs list-screens <projectId>');
          process.exit(1);
        }
        const res = await callStitchTool('list_screens', { projectId: args[0] });
        console.log(JSON.stringify(res, null, 2));
        break;
      }
      case 'get-screen': {
        if (!args[0]) {
          console.error('Usage: node stitch_client.mjs get-screen <screenName (e.g. projects/.../screens/...)>');
          process.exit(1);
        }
        const res = await callStitchTool('get_screen', { name: args[0] });
        console.log(JSON.stringify(res, null, 2));
        break;
      }
      case 'generate-screen': {
        if (!args[0] || !args[1]) {
          console.error('Usage: node stitch_client.mjs generate-screen <projectId> <prompt>');
          process.exit(1);
        }
        console.log(`Generating screen in project ${args[0]}... (this may take 1-2 minutes)`);
        const res = await callStitchTool('generate_screen_from_text', {
          projectId: args[0],
          prompt: args.slice(1).join(' '),
        });
        console.log(JSON.stringify(res, null, 2));
        break;
      }
      case 'list-design-systems': {
        if (!args[0]) {
          console.error('Usage: node stitch_client.mjs list-design-systems <projectId>');
          process.exit(1);
        }
        const res = await callStitchTool('list_design_systems', { projectId: args[0] });
        console.log(JSON.stringify(res, null, 2));
        break;
      }
      default:
        console.log(`Stitch MCP CLI Utility
Commands:
  list-projects                           List all projects in Stitch
  create-project [title]                  Create a new project
  get-project <projectName>               Get details for a project
  list-screens <projectId>                List screens for a given project ID
  get-screen <screenResourceName>         Retrieve screen metadata and URLs
  generate-screen <projectId> <prompt>    Generate a new UI screen from a text description
  list-design-systems <projectId>         List design systems for a project
`);
    }
  }

  run().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
