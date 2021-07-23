-- Tenants

create table if not exists tenants
(
    tenantId varchar(255) not null,
    createdOn timestamp,
    createdBy varchar(255),
    organizationId varchar(255) not null,
    name varchar(512),
    description varchar(2048),
    status varchar(255)
);

alter table tenants add constraint pk_tenants primary key (tenantId);

-- TenantLimits

create table if not exists tenantlimits
(
    id bigserial not null,
    resourcetype varchar(255),
    resourcelimit bigint,
    tenantId varchar(255) not null
);

alter table tenantlimits add constraint pk_tenantlimits primary key (id);
alter table tenantlimits add constraint fk_tenantlimits_1 foreign key (tenantId) references tenants (tenantId);