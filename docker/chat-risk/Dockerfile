FROM node:18-alpine AS builder

RUN corepack enable && corepack prepare pnpm@8.15.4 --activate

WORKDIR /app
COPY yudao-ui/yudao-ui-chat-risk/package.json yudao-ui/yudao-ui-chat-risk/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY yudao-ui/yudao-ui-chat-risk/ ./

COPY docker/chat-risk/.env.production .env.production

RUN pnpm build

FROM nginx:1.27-alpine

COPY docker/chat-risk/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
