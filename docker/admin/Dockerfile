FROM node:18-alpine AS builder

RUN corepack enable && corepack prepare pnpm@8.15.4 --activate

WORKDIR /app
COPY yudao-ui/yudao-ui-admin-vue3-master/package.json yudao-ui/yudao-ui-admin-vue3-master/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY yudao-ui/yudao-ui-admin-vue3-master/ ./

COPY docker/admin/.env.prod .env.prod

RUN pnpm build:prod

FROM nginx:1.27-alpine

COPY docker/admin/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist-prod /usr/share/nginx/html

EXPOSE 80
