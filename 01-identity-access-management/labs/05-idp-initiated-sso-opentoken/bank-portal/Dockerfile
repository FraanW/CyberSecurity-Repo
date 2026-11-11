# Partner Bank Portal (lab 05). No npm dependencies, so there is nothing to install.
FROM node:22-alpine
WORKDIR /app
COPY package.json server.js opentoken.js users.json ./
USER node
ENV NODE_ENV=production
CMD ["node", "server.js"]
