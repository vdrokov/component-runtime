/**
 *  Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */import React from 'react';
import PropTypes from 'prop-types';
import classnames from 'classnames';
import { withRouter, Link } from 'react-router-dom';

import ComponentsContext from '../../ComponentsContext';
import DatastoreContext from '../../DatastoreContext';
import DatasetContext from '../../DatasetContext';

import theme from './SideMenu.scss';

function activateIO(service, datastore, dataset) {
	return event => {
		event.preventDefault();
		service.activateIO(datastore, dataset);
	};
}

function SideMenu(props) {
	return (
		<nav className={theme.menu}>
			<ol>
				<li
					className={classnames({
						[theme.active]:
							props.location.pathname === '/' || props.location.pathname === '/project',
					})}
				>
					<Link to="/project" id="step-start">Start</Link>
				</li>
				<ComponentsContext.Consumer>
					{components => {
						if (components.withIO) {
							return (
								<React.Fragment>
									<li
										id="step-datastore"
										className={classnames({
											[theme.active]: props.location.pathname === '/datastore',
										})}
									>
										<Link to="/datastore">Datastore</Link>
									</li>
									<li
										id="step-dataset"
										className={classnames({
											[theme.active]: props.location.pathname === '/dataset',
										})}
									>
										<Link to="/dataset">Dataset</Link>
									</li>
								</React.Fragment>
							);
						}
						return (
							<li id="step-activate-io">
								<DatastoreContext.Consumer>
									{datastore => (
										<DatasetContext.Consumer>
											{dataset => (
												<a href="#/createNew" onClick={activateIO(components, datastore, dataset)}>
													Activate IO
												</a>
											)}
										</DatasetContext.Consumer>
									)}
								</DatastoreContext.Consumer>
							</li>
						);
					}}
				</ComponentsContext.Consumer>
				<ComponentsContext.Consumer>
					{components =>
						components.components.map((component, i) => (
							<li
								id={`step-component-${i}`}
								className={classnames({
									[theme.active]: props.location.pathname === `/component/${i}`,
								})}
								key={i}
							>
								<Link to={`/component/${i}`}>{component.configuration.name}</Link>
							</li>
						))
					}
				</ComponentsContext.Consumer>
				<ComponentsContext.Consumer>
					{components => (
						<li id="step-add-component">
							<Link to="/component/last" onClick={() => components.addComponent()}>
								Add A Component
							</Link>
						</li>
					)}
				</ComponentsContext.Consumer>
				<li
					id="step-finish"
					className={classnames({
						[theme.active]: props.location.pathname === '/export',
					})}
				>
					<Link to="/export" id="go-to-finish-button">Finish</Link>
				</li>
			</ol>
		</nav>
	);
}

SideMenu.displayName = 'SideMenu';
SideMenu.propTypes = {
	location: PropTypes.shape({
		pathname: PropTypes.string,
	}),
};

export default withRouter(SideMenu);
