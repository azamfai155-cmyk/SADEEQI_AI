import { app } from './server.js';

const port = Number(process.env.PORT || 10000);

app.listen(port, '0.0.0.0', () => {
  console.log(`SADEEQI API listening on port ${port}`);
});
